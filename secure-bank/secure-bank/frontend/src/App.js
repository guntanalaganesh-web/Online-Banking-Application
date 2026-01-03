import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Provider } from 'react-redux';
import { Toaster } from 'react-hot-toast';
import { store } from './store';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Accounts from './pages/Accounts';
import Transfers from './pages/Transfers';
import Transactions from './pages/Transactions';
import Login from './pages/Login';
import { useAuth } from './hooks/useAuth';

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? children : <Navigate to="/login" />;
}

function App() {
  return (
    <Provider store={store}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }>
            <Route index element={<Dashboard />} />
            <Route path="accounts" element={<Accounts />} />
            <Route path="transfers" element={<Transfers />} />
            <Route path="transactions" element={<Transactions />} />
          </Route>
        </Routes>
        <Toaster position="top-right" />
      </BrowserRouter>
    </Provider>
  );
}

export default App;
