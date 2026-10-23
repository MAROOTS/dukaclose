package com.dukaclose.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"0712345678", "0712 345 678", "254712345678", "+254712345678", "+254-712-345-678"})
    void allSpellingsOfOneNumberNormaliseTheSame(String raw) {
        assertThat(PhoneNumbers.normalise(raw)).isEqualTo("+254712345678");
    }

    @Test
    void newerOneSeriesNumbersWork() {
        assertThat(PhoneNumbers.normalise("0112345678")).isEqualTo("+254112345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "0912345678", "+255712345678", "07123456789", "abcdefghij"})
    void invalidNumbersAreRejected(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.normalise(raw)).isInstanceOf(ApiException.class);
    }

    @Test
    void lenientNeverThrows() {
        assertThat(PhoneNumbers.lenient("  nonsense ")).isEqualTo("nonsense");
        assertThat(PhoneNumbers.lenient(null)).isEmpty();
    }
}