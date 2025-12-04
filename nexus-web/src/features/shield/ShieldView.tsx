import React from 'react';
import { Shield, Globe, Wifi, Power } from 'lucide-react';
import { GlassCard } from '../../components/ui/GlassCard';
import { NeonButton } from '../../components/ui/NeonButton';

export const ShieldView = ({ isActive, onToggle }: { isActive: boolean, onToggle: () => void }) => (
    <div className="flex flex-col h-full items-center justify-between py-6 animate-in fade-in duration-500">
      <GlassCard className="w-full flex items-center justify-between mb-8" borderColor={isActive ? "cyan" : "white"}>
        <div className="flex items-center space-x-3">
          <div className={`w-3 h-3 rounded-full ${isActive ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`} />
          <span className="text-gray-300 font-mono text-sm">{isActive ? 'ENCRYPTED' : 'UNPROTECTED'}</span>
        </div>
      </GlassCard>

      <div className="relative flex items-center justify-center w-72 h-72 my-8">
        <div className={`absolute inset-0 rounded-full transition-all duration-1000 ${isActive ? 'bg-cyan-500/10 blur-3xl' : 'bg-transparent'}`} />
        <div className={`relative z-10 w-40 h-40 rounded-full flex items-center justify-center bg-gray-800/50`}>
          <Shield size={80} className={`transition-all duration-500 ${isActive ? 'text-cyan-400' : 'text-gray-600'}`} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 w-full mb-8">
        <GlassCard className="flex flex-col items-center justify-center py-4 space-y-2">
          <Globe size={20} className="text-cyan-400" />
          <span className="text-xs text-gray-500 uppercase">Region</span>
          <span className="text-gray-200 font-bold">{isActive ? 'Nexus-1' : 'Unknown'}</span>
        </GlassCard>
        <GlassCard className="flex flex-col items-center justify-center py-4 space-y-2">
          <Wifi size={20} className="text-cyan-400" />
          <span className="text-xs text-gray-500 uppercase">Protocol</span>
          <span className="text-gray-200 font-bold">ADB-SEC</span>
        </GlassCard>
      </div>

      <div className="w-full mt-auto">
        <NeonButton onClick={onToggle} active={isActive} icon={Power} label={isActive ? "DEACTIVATE" : "ACTIVATE"} color={isActive ? "cyan" : "gray"} />
      </div>
    </div>
);