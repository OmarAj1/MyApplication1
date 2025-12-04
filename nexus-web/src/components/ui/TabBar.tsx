import React from 'react';
import { Smartphone, Shield, History as HistoryIcon, User as UserIcon } from 'lucide-react';

interface TabBarProps {
  active: string;
  onChange: (id: string) => void;
}

const TabBar = ({ active, onChange }: TabBarProps) => (
  <div className="fixed bottom-0 left-0 right-0 z-50 pointer-events-none">
    <div className="absolute bottom-0 w-full h-48 bg-gradient-to-t from-[#020617] via-[#020617]/95 to-transparent" />
    <div className="pointer-events-auto relative flex justify-center pb-6 pt-2">
      <div className="flex items-center p-1.5 rounded-full bg-[#0f172a]/80 backdrop-blur-2xl border border-white/[0.1] shadow-2xl">
        {[{id:'purge',icon:Smartphone},{id:'shield',icon:Shield},{id:'history',icon:HistoryIcon},{id:'user',icon:UserIcon}].map((tab) => {
          const isActive = active === tab.id;
          return (
            <button key={tab.id} onClick={() => onChange(tab.id)}
              className={`relative flex items-center justify-center rounded-full transition-all duration-300 border px-5 py-3.5 ${isActive ? 'bg-cyan-500/20 text-cyan-400 border-cyan-500/20' : 'text-gray-500 border-transparent'}`}>
              <tab.icon size={22} strokeWidth={isActive ? 2.5 : 2} />
            </button>
          );
        })}
      </div>
    </div>
  </div>
);
export default TabBar;