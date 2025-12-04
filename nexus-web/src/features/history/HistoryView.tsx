import React from 'react';
import { ClipboardCopy } from 'lucide-react';
import { GlassCard } from '../../components/ui/GlassCard';
import { ActionLog } from '../../types';

export const HistoryView = ({ history, onExport }: { history: ActionLog[], onExport: () => void }) => (
      <div className="space-y-4 animate-in fade-in">
          <GlassCard borderColor="purple">
              <div className="flex justify-between items-center mb-4">
                  <h2 className="text-xl font-bold text-white">Action History</h2>
                  <button onClick={onExport} className="bg-purple-500/20 text-purple-300 p-2 rounded-lg flex gap-2 items-center text-xs"><ClipboardCopy size={14}/> Export</button>
              </div>
              <div className="space-y-2 h-[60vh] overflow-y-auto">
                   {history.map((h, i) => (
                      <div key={i} className="bg-white/5 p-3 rounded-lg flex justify-between items-center">
                          <div>
                              <p className="text-white text-sm font-mono">{h.pkg}</p>
                              <p className="text-[10px] text-gray-400">{h.timestamp}</p>
                          </div>
                      </div>
                  ))}
              </div>
          </GlassCard>
      </div>
);
