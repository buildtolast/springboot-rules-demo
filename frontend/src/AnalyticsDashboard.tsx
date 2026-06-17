import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { 
  Activity, 
  BarChart3, 
  RefreshCw, 
  AlertCircle,
  TrendingUp,
  Hash,
  CheckCircle2
} from 'lucide-react';
import type { AnalyticsStats } from './types';

const AnalyticsDashboard: React.FC = () => {
  const [stats, setStats] = useState<AnalyticsStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [range, setRange] = useState<string>('24h');

  const fetchStats = async () => {
    setLoading(true);
    try {
      let from: string | null = null;
      const now = new Date();
      if (range === '1h') from = new Date(now.getTime() - 3600000).toISOString();
      else if (range === '6h') from = new Date(now.getTime() - 6 * 3600000).toISOString();
      else if (range === '24h') from = new Date(now.getTime() - 24 * 3600000).toISOString();
      
      const response = await axios.get<AnalyticsStats>('/api/analytics/stats', {
        params: { from }
      });
      setStats(response.data);
      setError(null);
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Unknown error';
      setError(`Failed to fetch analytics: ${msg}`);
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 30000); // Auto refresh every 30s
    return () => clearInterval(interval);
  }, [range]);

  if (loading && !stats) {
    return (
      <div className="flex flex-col items-center justify-center py-24">
        <div className="w-12 h-12 border-4 border-indigo-100 border-t-indigo-600 rounded-full animate-spin mb-4"></div>
        <span className="text-lg font-medium text-gray-500">Calculating statistics...</span>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {/* Analytics Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <TrendingUp size={24} className="text-indigo-600" />
            System Performance
          </h2>
          <p className="text-gray-500">Real-time evaluation metrics and rule performance.</p>
        </div>
        
        <div className="flex items-center gap-3">
          <div className="bg-white p-1 rounded-2xl border border-gray-200 flex shadow-sm">
            {(['1h', '6h', '24h'] as const).map((r) => (
              <button 
                key={r}
                onClick={() => setRange(r)}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${range === r ? 'bg-indigo-600 text-white shadow-md' : 'text-gray-500 hover:bg-gray-50'}`}
              >
                Last {r}
              </button>
            ))}
          </div>
          <button 
            onClick={fetchStats}
            className="p-2.5 text-gray-500 hover:text-indigo-600 hover:bg-white hover:shadow-sm border border-transparent hover:border-gray-200 rounded-xl transition-all"
          >
            <RefreshCw size={20} className={loading ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-2xl p-4 flex items-start gap-3">
          <AlertCircle className="text-red-500 mt-0.5" size={20} />
          <p className="text-red-700 font-medium">{error}</p>
        </div>
      )}

      {/* High-level Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard 
          icon={<Activity className="text-blue-600" size={24} />}
          label="Processed Messages"
          value={stats?.totalMessages || 0}
          bgColor="bg-blue-50"
        />
        <StatCard 
          icon={<Hash className="text-purple-600" size={24} />}
          label="Total Evaluations"
          value={stats?.totalEvaluations || 0}
          bgColor="bg-purple-50"
        />
        <StatCard 
          icon={<CheckCircle2 className="text-emerald-600" size={24} />}
          label="Matches"
          value={stats?.ruleStats.reduce((acc, curr) => acc + curr.matched, 0) || 0}
          bgColor="bg-emerald-50"
        />
        <StatCard 
          icon={<AlertCircle className="text-red-600" size={24} />}
          label="Errors"
          value={stats?.ruleStats.reduce((acc, curr) => acc + curr.errored, 0) || 0}
          bgColor="bg-red-50"
        />
      </div>

      {/* Rule Breakdown */}
      <div className="bg-white rounded-[2rem] shadow-xl shadow-gray-200/50 border border-gray-100 overflow-hidden">
        <div className="px-8 py-6 border-b border-gray-50">
          <h3 className="text-xl font-bold text-gray-900 flex items-center gap-2">
            <BarChart3 size={20} className="text-indigo-600" />
            Rule Performance Breakdown
          </h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-50/50 border-b border-gray-100">
                <th className="px-8 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Rule Identifier</th>
                <th className="px-8 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Efficiency</th>
                <th className="px-8 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Matched</th>
                <th className="px-8 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Unmatched</th>
                <th className="px-8 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Errored</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {stats?.ruleStats.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-8 py-12 text-center text-gray-500 font-medium">
                    No evaluations recorded for this time range.
                  </td>
                </tr>
              ) : (
                stats?.ruleStats.map((rs) => {
                  const total = rs.matched + rs.unmatched + rs.errored;
                  const matchRate = total > 0 ? ((rs.matched / total) * 100).toFixed(1) : '0';
                  
                  return (
                    <tr key={rs.ruleId} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-8 py-5 font-mono text-sm font-bold text-gray-700">{rs.ruleId}</td>
                      <td className="px-8 py-5">
                        <div className="w-full bg-gray-100 rounded-full h-2.5 max-w-[120px]">
                          <div 
                            className="bg-indigo-600 h-2.5 rounded-full" 
                            style={{ width: `${matchRate}%` }}
                          ></div>
                        </div>
                        <span className="text-[10px] font-black text-indigo-600 mt-1 block uppercase tracking-tighter">{matchRate}% match rate</span>
                      </td>
                      <td className="px-8 py-5">
                        <span className="text-emerald-600 font-black text-lg">{rs.matched}</span>
                      </td>
                      <td className="px-8 py-5">
                        <span className="text-gray-400 font-bold">{rs.unmatched}</span>
                      </td>
                      <td className="px-8 py-5">
                        <span className={`font-bold ${rs.errored > 0 ? 'text-red-500' : 'text-gray-300'}`}>{rs.errored}</span>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: number;
  bgColor: string;
}

const StatCard: React.FC<StatCardProps> = ({ icon, label, value, bgColor }) => (
  <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 flex items-center gap-5">
    <div className={`w-14 h-14 ${bgColor} rounded-2xl flex items-center justify-center shadow-inner`}>
      {icon}
    </div>
    <div>
      <p className="text-sm font-bold text-gray-500 uppercase tracking-wider">{label}</p>
      <h3 className="text-3xl font-black text-gray-900">{value.toLocaleString()}</h3>
    </div>
  </div>
);

export default AnalyticsDashboard;
