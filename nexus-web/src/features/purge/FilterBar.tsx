import React from 'react';
import { Search, LogOut } from 'lucide-react';

interface FilterBarProps {
  search: string;
  setSearch: (value: string) => void;
  // UPDATE 1: Added 'category' to filters object so it matches the hook
  filters: {
    safety: string;
    status: string;
    userId: number;
    category: string;
  };

  // UPDATE 2: THIS IS THE CRITICAL FIX
  // We changed 'key: string' to the specific allowed keys
  updateFilter: (key: "status" | "category" | "safety" | "userId", value: any) => void;

  users: { id: number; name: string }[];
  onDisconnect: () => void;
}

export const FilterBar = ({
  search,
  setSearch,
  filters,
  updateFilter,
  users,
  onDisconnect
}: FilterBarProps) => {

  const selectStyle = "bg-[#1e293b] border border-white/10 rounded-lg text-white text-xs px-2 py-2 outline-none focus:border-cyan-500 transition-colors";

  return (
    <div className="flex flex-col gap-2 mb-4 animate-in slide-in-from-top-5">

      {/* TOP ROW: Search & Disconnect */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3 top-2.5 text-gray-500" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search packages..."
            className="w-full bg-[#1e293b] border border-white/10 rounded-lg py-2 pl-9 pr-4 text-sm text-white focus:outline-none focus:border-cyan-500 transition-all"
          />
        </div>
        <button
          onClick={onDisconnect}
          className="bg-red-500/10 hover:bg-red-500/20 text-red-400 p-2 rounded-lg border border-red-500/10 transition-colors"
          title="Disconnect ADB"
        >
          <LogOut size={18} />
        </button>
      </div>

      {/* BOTTOM ROW: Filters */}
      <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">

        {/* User Select */}
        <select
          value={filters.userId}
          onChange={(e) => updateFilter('userId', parseInt(e.target.value))}
          className={selectStyle}
        >
          {users.map(u => (
            <option key={u.id} value={u.id}>User {u.id} ({u.name})</option>
          ))}
        </select>

        {/* Safety Select */}
        <select
          value={filters.safety}
          onChange={(e) => updateFilter('safety', e.target.value)}
          className={selectStyle}
        >
          {['All', 'Recommended', 'Advanced', 'Expert', 'Unsafe'].map(f => (
            <option key={f} value={f}>{f === 'All' ? 'Safety: All' : f}</option>
          ))}
        </select>

        {/* Status Select */}
        <select
          value={filters.status}
          onChange={(e) => updateFilter('status', e.target.value)}
          className={selectStyle}
        >
          {['All', 'Enabled', 'Disabled', 'Uninstalled'].map(f => (
            <option key={f} value={f}>{f === 'All' ? 'Status: All' : f}</option>
          ))}
        </select>
      </div>
    </div>
  );
};