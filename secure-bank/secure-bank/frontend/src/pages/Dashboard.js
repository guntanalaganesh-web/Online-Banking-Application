import React from 'react';
import { motion } from 'framer-motion';
import { 
  BanknotesIcon, ArrowTrendingUpIcon, ArrowTrendingDownIcon,
  CreditCardIcon, ShieldCheckIcon, ClockIcon 
} from '@heroicons/react/24/outline';
import { Line, Doughnut } from 'react-chartjs-2';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

const stats = [
  { name: 'Total Balance', value: '$124,523.00', icon: BanknotesIcon, change: '+2.5%', up: true },
  { name: 'Monthly Income', value: '$8,250.00', icon: ArrowTrendingUpIcon, change: '+12%', up: true },
  { name: 'Monthly Expenses', value: '$3,420.00', icon: ArrowTrendingDownIcon, change: '-5%', up: false },
  { name: 'Active Cards', value: '3', icon: CreditCardIcon, change: '', up: true },
];

const recentTransactions = [
  { id: 1, name: 'Amazon Purchase', amount: -89.99, date: 'Today', status: 'completed' },
  { id: 2, name: 'Salary Deposit', amount: 5200.00, date: 'Yesterday', status: 'completed' },
  { id: 3, name: 'SWIFT Transfer', amount: -1500.00, date: 'Dec 28', status: 'pending' },
  { id: 4, name: 'Utility Bill', amount: -125.50, date: 'Dec 27', status: 'completed' },
];

export default function Dashboard() {
  const lineData = {
    labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
    datasets: [{
      label: 'Balance',
      data: [95000, 102000, 98000, 115000, 120000, 124523],
      borderColor: '#3b82f6',
      backgroundColor: 'rgba(59, 130, 246, 0.1)',
      fill: true,
      tension: 0.4,
    }]
  };

  const doughnutData = {
    labels: ['Checking', 'Savings', 'Investment'],
    datasets: [{
      data: [45000, 55000, 24523],
      backgroundColor: ['#3b82f6', '#10b981', '#f59e0b'],
    }]
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-white">Dashboard</h1>
        <div className="flex items-center gap-2 text-green-400">
          <ShieldCheckIcon className="w-5 h-5" />
          <span className="text-sm">All systems secure</span>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat, i) => (
          <motion.div
            key={stat.name}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
            className="bg-slate-800 border border-slate-700 rounded-xl p-6"
          >
            <div className="flex items-center justify-between">
              <stat.icon className="w-8 h-8 text-blue-400" />
              {stat.change && (
                <span className={`text-sm ${stat.up ? 'text-green-400' : 'text-red-400'}`}>
                  {stat.change}
                </span>
              )}
            </div>
            <p className="mt-4 text-2xl font-bold text-white">{stat.value}</p>
            <p className="text-slate-400 text-sm">{stat.name}</p>
          </motion.div>
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-slate-800 border border-slate-700 rounded-xl p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Balance History</h3>
          <Line data={lineData} options={{ 
            responsive: true, 
            plugins: { legend: { display: false } },
            scales: { 
              y: { grid: { color: '#334155' }, ticks: { color: '#94a3b8' } },
              x: { grid: { display: false }, ticks: { color: '#94a3b8' } }
            }
          }} />
        </div>
        <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Account Distribution</h3>
          <Doughnut data={doughnutData} options={{ 
            responsive: true,
            plugins: { legend: { position: 'bottom', labels: { color: '#94a3b8' } } }
          }} />
        </div>
      </div>

      {/* Recent Transactions */}
      <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
        <h3 className="text-lg font-semibold text-white mb-4">Recent Transactions</h3>
        <div className="space-y-3">
          {recentTransactions.map((tx) => (
            <div key={tx.id} className="flex items-center justify-between p-3 bg-slate-700/50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className={`w-10 h-10 rounded-full flex items-center justify-center ${
                  tx.amount > 0 ? 'bg-green-500/20' : 'bg-red-500/20'
                }`}>
                  {tx.amount > 0 ? <ArrowTrendingUpIcon className="w-5 h-5 text-green-400" /> 
                                 : <ArrowTrendingDownIcon className="w-5 h-5 text-red-400" />}
                </div>
                <div>
                  <p className="text-white font-medium">{tx.name}</p>
                  <p className="text-slate-400 text-sm">{tx.date}</p>
                </div>
              </div>
              <div className="text-right">
                <p className={`font-semibold ${tx.amount > 0 ? 'text-green-400' : 'text-white'}`}>
                  {tx.amount > 0 ? '+' : ''}{tx.amount.toLocaleString('en-US', { style: 'currency', currency: 'USD' })}
                </p>
                <span className={`text-xs px-2 py-1 rounded-full ${
                  tx.status === 'completed' ? 'bg-green-500/20 text-green-400' : 'bg-yellow-500/20 text-yellow-400'
                }`}>{tx.status}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
