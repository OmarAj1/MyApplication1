import React from 'react';
import { Smartphone, Trash2, Power } from 'lucide-react';
import { useAppManager } from './useAppManager';
import { FilterBar } from './FilterBar';
import { AppData } from '../../types';

// 1. Add 'onAction' to the interface
interface PurgeViewProps {
  allApps: AppData[];
  users: { id: number; name: string }[];
  onDisconnect: () => void;
  // action: "uninstall" | "disable" | "enable" | "restore"
  onAction: (action: string, pkg: string, userId: number) => void;
}

export const PurgeView = ({ allApps, users, onDisconnect, onAction }: PurgeViewProps) => {
  const { search, setSearch, filters, updateFilter, filteredApps } = useAppManager(allApps);

  return (
    <div className="flex flex-col h-full pt-2">
      <FilterBar
        search={search} setSearch={setSearch}
        filters={filters} updateFilter={updateFilter}
        users={users} onDisconnect={onDisconnect}
      />

      <div className="flex-1 overflow-y-auto space-y-2 pb-20">
        {filteredApps.length === 0 ? (
            <div className="text-center text-gray-500 mt-10">No apps found matching filters.</div>
        ) : filteredApps.map((app) => (
            <div key={app.pkg} className="bg-[#1e293b]/40 border border-white/[0.05] rounded-lg p-3 hover:bg-[#1e293b]/60">
                <div className="flex justify-between items-start mb-2">
                    <div className="flex items-center overflow-hidden">
                        {/* Icon & Text ... */}
                        <div className="overflow-hidden pr-2">
                            <h3 className="font-semibold text-white text-sm truncate">{app.name}</h3>
                            <p className="text-[10px] text-gray-500 font-mono truncate">{app.pkg}</p>
                        </div>
                    </div>
                </div>

                {/* 2. FIXED: Wired up the buttons to 'onAction' */}
                <div className="flex gap-2 justify-end pt-2 border-t border-white/5">
                    <button
                        onClick={() => onAction('disable', app.pkg, 0)}
                        className="p-1.5 bg-amber-500/10 text-amber-400 rounded hover:bg-amber-500/20 active:scale-95 transition-all">
                        <Power size={14}/>
                    </button>
                    <button
                        onClick={() => onAction('uninstall', app.pkg, 0)}
                        className="p-1.5 bg-red-500/10 text-red-400 rounded hover:bg-red-500/20 active:scale-95 transition-all">
                        <Trash2 size={14}/>
                    </button>
                </div>
            </div>
        ))}
      </div>
    </div>
  );
};