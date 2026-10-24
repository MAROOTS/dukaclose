import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth, RequireOwner } from './auth/Guards'
import AppLayout from './layout/AppLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import ShopsPage from './pages/ShopsPage'

export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<RequireAuth />}>
                <Route element={<AppLayout />}>
                    <Route index element={<HomePage />} />
                    <Route element={<RequireOwner />}>
                        <Route path="shops" element={<ShopsPage />} />
                    </Route>
                </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}