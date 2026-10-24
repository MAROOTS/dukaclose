package com.dukaclose.api.inventory;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.RecordStatus;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    static final int MAX_ROWS = 1000;
    private static final int MAX_ERRORS_SHOWN = 100;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999");
    private static final long MAX_PRICE_MINOR = 1_000_000_000L;
    private static final long MAX_COST_MINOR = 10_000_000_000L;

    private static final Pattern MONEY = Pattern.compile("^\\d{1,12}(\\.\\d{1,2})?$");
    private static final Pattern DECIMAL = Pattern.compile("^\\d{1,12}(\\.\\d{1,6})?$");
    // a comma is only accepted as a thousand separator: "1,200" yes, "1,5" no (could mean 1.5 elsewhere)
    private static final Pattern THOUSANDS = Pattern.compile("^\\d{1,3}(,\\d{3})+(\\.\\d+)?$");

    private static final Set<String> KNOWN = Set.of(
            "name", "unit", "price", "quantity", "cost_per_unit", "reorder_level", "pack_name", "pack_size");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("item", "name"), Map.entry("item_name", "name"), Map.entry("product", "name"),
            Map.entry("selling_price", "price"), Map.entry("sell_price", "price"),
            Map.entry("qty", "quantity"), Map.entry("stock", "quantity"),
            Map.entry("opening_stock", "quantity"), Map.entry("on_hand", "quantity"),
            Map.entry("cost", "cost_per_unit"), Map.entry("unit_cost", "cost_per_unit"),
            Map.entry("buying_price", "cost_per_unit"),
            Map.entry("reorder", "reorder_level"), Map.entry("reorder_at", "reorder_level"),
            Map.entry("pack", "pack_name"));

    private static final Map<String, Unit> UNIT_WORDS = Map.ofEntries(
            Map.entry("kg", Unit.KG), Map.entry("kgs", Unit.KG), Map.entry("kilo", Unit.KG),
            Map.entry("kilos", Unit.KG), Map.entry("kilogram", Unit.KG), Map.entry("kilograms", Unit.KG),
            Map.entry("l", Unit.LITRE), Map.entry("lt", Unit.LITRE), Map.entry("ltr", Unit.LITRE),
            Map.entry("litre", Unit.LITRE), Map.entry("litres", Unit.LITRE),
            Map.entry("liter", Unit.LITRE), Map.entry("liters", Unit.LITRE),
            Map.entry("piece", Unit.PIECE), Map.entry("pieces", Unit.PIECE), Map.entry("pc", Unit.PIECE),
            Map.entry("pcs", Unit.PIECE), Map.entry("each", Unit.PIECE), Map.entry("unit", Unit.PIECE),
            Map.entry("units", Unit.PIECE));

    private final AccessService access;
    private final ItemRepository items;
    private final ShopInventoryRepository inventory;
    private final StockMovementRepository movements;
    private final StockService stockService;

    private record RawRow(int line, Map<String, String> cells) {
        String get(String column) { return cells.getOrDefault(column, ""); }
        boolean blank() { return cells.values().stream().allMatch(String::isEmpty); }
    }

    private record Parsed(Set<String> present, List<String> ignored, List<RawRow> rows) {}

    /** A row that passed every check. */
    private record Row(int line, String name, Unit unit, String packName, BigDecimal packSize,
                       Long priceMinor, BigDecimal reorderLevel, BigDecimal quantity, Long totalCostMinor,
                       UUID requestId, Item existing, boolean alreadyApplied) {}

    private static final class Problems {
        final List<ImportReport.Message> errors = new ArrayList<>();
        final List<ImportReport.Message> warnings = new ArrayList<>();
        int errorCount;

        void error(int line, String message) {
            errorCount++;
            if (errors.size() < MAX_ERRORS_SHOWN) errors.add(new ImportReport.Message(line, message));
        }

        void warn(int line, String message) {
            if (warnings.size() < MAX_ERRORS_SHOWN) warnings.add(new ImportReport.Message(line, message));
        }
    }

    // ------------------------------------------------------------------ entry point

    @Transactional
    public ImportReport importCsv(UUID userId, UUID shopId, UUID importId, String csv, boolean commit) {
        Shop shop = access.requireShopOwner(userId, shopId).shop();
        UUID businessId = shop.getBusinessId();

        Parsed parsed = parse(csv);
        long nonBlank = parsed.rows().stream().filter(r -> !r.blank()).count();
        if (nonBlank == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "The file has a header but no items. Add at least one row.");
        }
        if (nonBlank > MAX_ROWS) {
            throw ApiException.badRequest("TOO_MANY_ROWS",
                    "A file can have up to " + MAX_ROWS + " items. Split it into smaller files.");
        }

        Problems problems = new Problems();
        parsed.ignored().forEach(c -> problems.warn(1, "The column \"" + c + "\" is not used and was ignored."));

        Map<String, Item> existing = new HashMap<>();
        for (Item item : items.findByBusinessIdOrderByNameAsc(businessId)) {
            existing.put(item.getName().toLowerCase(Locale.ROOT), item);
        }

        Map<String, Integer> firstSeen = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (RawRow raw : parsed.rows()) {
            if (raw.blank()) continue;
            Row row = validate(raw, shopId, importId, existing, firstSeen, problems);
            if (row != null) rows.add(row);
        }

        int newItems = (int) rows.stream().filter(r -> r.existing() == null).count();
        List<Row> openings = rows.stream().filter(r -> r.quantity() != null && !r.alreadyApplied()).toList();
        long openingValue = openings.stream().mapToLong(Row::totalCostMinor).sum();

        boolean committed = false;
        if (commit && problems.errorCount == 0) {
            apply(userId, shopId, businessId, rows);
            committed = true;
        }
        return new ImportReport(committed, (int) nonBlank, newItems, rows.size() - newItems,
                openings.size(), openingValue, problems.errorCount, problems.errors, problems.warnings);
    }

    // ---------------------------------------------------------------------- apply

    private void apply(UUID userId, UUID shopId, UUID businessId, List<Row> rows) {
        for (Row r : rows) {
            Item item = r.existing() != null
                    ? r.existing()
                    : items.save(new Item(businessId, r.name(), r.unit(), r.packName(), r.packSize()));

            if (r.quantity() != null && !r.alreadyApplied()) {
                stockService.openingStock(userId, shopId, item.getId(), new StockInRequest(
                        r.requestId(), r.quantity(), null, r.totalCostMinor(), "Imported from CSV"));
            }

            if (r.priceMinor() != null || r.reorderLevel() != null) {
                ShopInventory row = inventory.findForUpdate(shopId, item.getId())
                        .orElseGet(() -> inventory.saveAndFlush(
                                new ShopInventory(shopId, businessId, item.getId(), null)));
                row.update(
                        r.priceMinor() != null ? r.priceMinor() : row.getPriceMinor(),
                        r.reorderLevel() != null ? r.reorderLevel() : row.getReorderLevel());
            }
        }
    }

    // ------------------------------------------------------------------- validation

    private Row validate(RawRow raw, UUID shopId, UUID importId, Map<String, Item> existing,
                         Map<String, Integer> firstSeen, Problems problems) {
        int before = problems.errorCount;
        int line = raw.line();

        String name = raw.get("name");
        if (name.isEmpty()) {
            problems.error(line, "Name is missing.");
        } else if (name.length() > 120) {
            problems.error(line, "Name is too long (120 characters at most).");
        } else {
            Integer first = firstSeen.putIfAbsent(name.toLowerCase(Locale.ROOT), line);
            if (first != null) {
                problems.error(line, "\"" + name + "\" appears twice (first on row " + first + ").");
            }
        }

        String unitText = raw.get("unit");
        Unit unit = UNIT_WORDS.get(unitText.toLowerCase(Locale.ROOT));
        if (unit == null) {
            problems.error(line, unitText.isEmpty()
                    ? "Unit is missing. Use kg, litre or piece."
                    : "Unit \"" + unitText + "\" is not recognised. Use kg, litre or piece.");
        }

        Item item = name.isEmpty() ? null : existing.get(name.toLowerCase(Locale.ROOT));
        if (item != null) {
            if (item.getStatus() == RecordStatus.INACTIVE) {
                problems.error(line, "\"" + item.getName() + "\" is archived. Restore it before importing.");
            } else if (unit != null && item.getUnit() != unit) {
                problems.error(line, "\"" + item.getName() + "\" already exists and is sold by "
                        + item.getUnit().name().toLowerCase(Locale.ROOT) + ", not " + unit.name().toLowerCase(Locale.ROOT) + ".");
            }
        }

        Long price = null;
        String priceText = raw.get("price");
        if (!priceText.isEmpty()) {
            price = parseMoney(priceText, MAX_PRICE_MINOR);
            if (price == null) {
                problems.error(line, "Price \"" + priceText + "\" is not a valid amount. Use a number like 190 or 190.50.");
            }
        }

        BigDecimal reorder = null;
        String reorderText = raw.get("reorder_level");
        if (!reorderText.isEmpty()) {
            reorder = parseDecimal(reorderText);
            if (reorder == null || reorder.compareTo(MAX_QUANTITY) > 0 || (unit != null && !unit.fitsScale(reorder))) {
                problems.error(line, "Reorder level \"" + reorderText + "\" is not valid"
                        + (unit == Unit.PIECE ? " (whole numbers only for pieces)." : "."));
                reorder = null;
            }
        }

        String packName = null;
        BigDecimal packSize = null;
        String packNameText = raw.get("pack_name");
        String packSizeText = raw.get("pack_size");
        if (!packNameText.isEmpty() || !packSizeText.isEmpty()) {
            if (item != null) {
                problems.warn(line, "Pack details were ignored because \"" + item.getName() + "\" already exists.");
            } else {
                BigDecimal size = parseDecimal(packSizeText);
                boolean bad = packNameText.isEmpty() || packNameText.length() > 40 || size == null
                        || size.signum() <= 0 || size.compareTo(MAX_QUANTITY) > 0
                        || (unit != null && !unit.fitsScale(size));
                if (bad) {
                    problems.error(line, "A pack needs both a name and a valid size, like Sack and 50.");
                } else {
                    packName = packNameText;
                    packSize = size;
                }
            }
        }

        BigDecimal quantity = null;
        Long totalCost = null;
        UUID requestId = null;
        boolean alreadyApplied = false;
        String quantityText = raw.get("quantity");
        if (!quantityText.isEmpty()) {
            BigDecimal q = parseDecimal(quantityText);
            if (q == null || q.compareTo(MAX_QUANTITY) > 0) {
                problems.error(line, "Quantity \"" + quantityText + "\" is not a valid number.");
            } else if (unit != null && !unit.fitsScale(q)) {
                problems.error(line, "Quantity " + quantityText + " is not allowed for "
                        + unit.name().toLowerCase(Locale.ROOT)
                        + (unit == Unit.PIECE ? ". Use a whole number." : ". Use at most 3 decimals."));
            } else if (q.signum() > 0) {          // a quantity of 0 simply means "no starting stock"
                String costText = raw.get("cost_per_unit");
                Long costMinor = costText.isEmpty() ? null : parseMoney(costText, MAX_COST_MINOR);
                if (costMinor == null) {
                    problems.error(line, costText.isEmpty()
                            ? "Add the cost per unit for starting stock (use 0 if it was free)."
                            : "Cost \"" + costText + "\" is not a valid amount.");
                } else {
                    BigDecimal total = BigDecimal.valueOf(costMinor).multiply(q).setScale(0, RoundingMode.HALF_UP);
                    if (total.compareTo(BigDecimal.valueOf(MAX_COST_MINOR)) > 0) {
                        problems.error(line, "That quantity and cost make a total that is too large.");
                    } else {
                        quantity = q;
                        totalCost = total.longValueExact();
                        requestId = UUID.nameUUIDFromBytes((importId + ":" + line).getBytes(StandardCharsets.UTF_8));
                        if (item != null) {
                            if (movements.findByShopIdAndRequestId(shopId, requestId).isPresent()) {
                                alreadyApplied = true;        // a retry of an import that already went through
                            } else if (movements.existsByShopIdAndItemId(shopId, item.getId())) {
                                problems.error(line, "\"" + item.getName()
                                        + "\" already has stock history in this shop. Leave the quantity empty on this row.");
                            }
                        }
                    }
                }
            }
        }

        if (problems.errorCount > before) return null;
        return new Row(line, name, unit, packName, packSize, price, reorder, quantity, totalCost,
                requestId, item, alreadyApplied);
    }

    // ---------------------------------------------------------------------- parsing

    private Parsed parse(String csv) {
        String text = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;   // Excel's byte-order mark
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(detectDelimiter(text))
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)          // keeps row numbers equal to spreadsheet rows
                .setAllowMissingColumnNames(true)
                .setTrim(true)
                .build();

        List<String> columns = new ArrayList<>();
        Set<String> present = new LinkedHashSet<>();
        List<String> ignored = new ArrayList<>();
        List<RawRow> rows = new ArrayList<>();

        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (String header : parser.getHeaderNames()) {
                String column = canonical(header);
                if (column != null && KNOWN.contains(column)) {
                    columns.add(column);
                    present.add(column);
                } else {
                    columns.add(null);
                    if (column != null) ignored.add(header.trim());
                }
            }
            int line = 1;                             // the header is row 1
            for (CSVRecord record : parser) {
                line++;
                Map<String, String> cells = new HashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    String column = columns.get(i);
                    if (column == null) continue;
                    String value = i < record.size() ? record.get(i) : "";
                    cells.putIfAbsent(column, value == null ? "" : value.trim());
                }
                rows.add(new RawRow(line, cells));
            }
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("INVALID_CSV",
                    "That file could not be read as a CSV. Save it as \"CSV UTF-8\" and try again.");
        }

        if (!present.contains("name") || !present.contains("unit")) {
            throw ApiException.badRequest("MISSING_COLUMNS",
                    "Your file needs at least the columns name and unit. The template shows the layout.");
        }
        return new Parsed(present, ignored, rows);
    }

    private static String canonical(String header) {
        if (header == null) return null;
        String h = header.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
        return h.isEmpty() ? null : ALIASES.getOrDefault(h, h);
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        long commas = first.chars().filter(c -> c == ',').count();
        long semicolons = first.chars().filter(c -> c == ';').count();
        long tabs = first.chars().filter(c -> c == '\t').count();
        if (semicolons > commas && semicolons >= tabs) return ';';
        if (tabs > commas && tabs > semicolons) return '\t';
        return ',';
    }

    private static String plainNumber(String text) {
        String t = text.trim();
        if (t.contains(",")) return THOUSANDS.matcher(t).matches() ? t.replace(",", "") : null;
        return t;
    }

    /** "190", "190.5", "1,200.50", "KSh 190" -> cents. Null when invalid or above the maximum. */
    private static Long parseMoney(String text, long maxMinor) {
        String t = plainNumber(text.replaceAll("(?i)^ksh\\.?\\s*", "").replaceAll("\\s", ""));
        if (t == null || !MONEY.matcher(t).matches()) return null;
        String[] parts = t.split("\\.");
        long cents = parts.length > 1 ? Long.parseLong((parts[1] + "0").substring(0, 2)) : 0;
        long minor = Long.parseLong(parts[0]) * 100 + cents;
        return minor <= maxMinor ? minor : null;
    }

    private static BigDecimal parseDecimal(String text) {
        String t = plainNumber(text);
        if (t == null || !DECIMAL.matcher(t).matches()) return null;
        return new BigDecimal(t);
    }
}