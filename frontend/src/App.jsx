import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth, RequireOwner } from './auth/Guards'
import AppLayout from './layout/AppLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import ShopsPage from './pages/ShopsPage'
import InventoryPage from './pages/InventoryPage'
import ItemPage from './pages/ItemPage'
import NewItemPage from './pages/NewItemPage'
import ImportPage from './pages/ImportPage'
import SalePage from './pages/SalePage'
import SalesPage from './pages/SalesPage'
import SellPage from './pages/SellPage'

export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<RequireAuth />}>
                <Route element={<AppLayout />}>
                    <Route index element={<HomePage />} />
                    <Route path="stock" element={<InventoryPage />} />
                    <Route path="stock/:itemId" element={<ItemPage />} />
                    <Route element={<RequireOwner />}>
                        <Route path="stock/new" element={<NewItemPage />} />
                        <Route path="stock/import" element={<ImportPage />} />
                        <Route path="shops" element={<ShopsPage />} />
                    </Route>
                    <Route path="sell" element={<SellPage />} />
                    <Route path="sales" element={<SalesPage />} />
                    <Route path="sales/:saleId" element={<SalePage />} />
                </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}