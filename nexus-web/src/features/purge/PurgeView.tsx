import React from 'react';
import { Smartphone, Trash2, Power, RotateCcw } from 'lucide-react';
import { useAppManager } from './useAppManager';
import { FilterBar } from './FilterBar';
import { AppData } from '../../types';
// import { Badge } from '../../components/ui/Badge'; // Ensure you have Badge component or remove this import

interface PurgeViewProps {
  allApps: AppData[];
  users: { id: number; name: string }[];
  onDisconnect: () => void;
}

export const PurgeView = ({ allApps, users, onDisconnect }: PurgeViewProps) => {
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
                        <div className="w-8 h-8 rounded-md mr-3 bg-gray-600/50 flex items-center justify-center">
                            {app.iconBase64 ? <img src={`data:image/png;base64,${app.iconBase64}`} className="w-8 h-8" /> : <Smartphone size={16} className="text-gray-400" />}
                        </div>
                        <div className="overflow-hidden pr-2">
                            <h3 className="font-semibold text-white text-sm truncate">{app.name}</h3>
                            <p className="text-[10px] text-gray-500 font-mono truncate">{app.pkg}</p>
                        </div>
                    </div>
                </div>
                {/* Quick Action Buttons */}
                <div className="flex gap-2 justify-end pt-2 border-t border-white/5">
                    <button className="p-1.5 bg-amber-500/10 text-amber-400 rounded"><Power size={14}/></button>
                    <button className="p-1.5 bg-red-500/10 text-red-400 rounded"><Trash2 size={14}/></button>
                </div>
            </div>
        ))}
      </div>
    </div>
  );
};