import React, { useState, useEffect } from 'react';
import { DownloadCloud, Zap, ArrowRight, Wifi } from 'lucide-react';
import { GlassCard } from '../../components/ui/GlassCard';

interface ConnectionViewProps {
    status: string;
    onPair: (ip: string, port: string, code: string) => void;
    onConnect: (ip: string, port: string) => void;
    onRetrieve: () => void;
    pairingData: { ip: string, port: string };
    connectData: { ip: string, port: string };
}

export const ConnectionView = ({
    status, onPair, onConnect, onRetrieve, pairingData, connectData
}: ConnectionViewProps) => {

  const [ip, setIp] = useState('');
  const [port, setPort] = useState('');
  const [code, setCode] = useState('');

  // Auto-fill fields when data comes in
  useEffect(() => {
      if (pairingData.ip) {
          // Priority 1: If pairing info is found, show that (so user can pair)
          setIp(pairingData.ip);
          setPort(pairingData.port);
      } else if (connectData.ip) {
          // Priority 2: If main info is found, show that
          setIp(connectData.ip);
          setPort(connectData.port);
      }
  }, [pairingData, connectData]);

  return (
    <div className="flex flex-col h-full justify-center space-y-6 animate-in fade-in duration-500">
      <GlassCard className="space-y-4" borderColor="cyan">

        {/* Header Status */}
        <div className="flex justify-between items-center pb-2 border-b border-white/10">
            <div className="flex items-center gap-2">
                <Wifi size={14} className={status.includes('Connect') ? "text-green-400" : "text-gray-400"} />
                <span className="text-xs font-mono text-gray-400">STATUS</span>
            </div>
            <span className="text-xs font-bold text-cyan-400 animate-pulse">{status}</span>
        </div>

        {/* Inputs */}
        <div className="space-y-2">
            <div className="flex gap-2">
                <input
                    value={ip}
                    onChange={e=>setIp(e.target.value)}
                    placeholder="192.168.x.x"
                    className="flex-1 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono transition-all"
                />
                <input
                    value={port}
                    onChange={e=>setPort(e.target.value)}
                    placeholder="PORT"
                    className="w-24 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono text-center transition-all"
                />
            </div>

            <button
                onClick={onRetrieve}
                className="w-full bg-white/5 text-cyan-400 p-2 rounded-lg border border-white/10 flex items-center justify-center gap-2 text-xs uppercase hover:bg-white/10 hover:border-cyan-500/50 transition-all"
            >
                <DownloadCloud size={14} /> Auto-Detect IP/Port
            </button>
        </div>

        {/* Pairing Section */}
        <div className="space-y-2 pt-2 border-t border-white/5">
            <input
                value={code}
                onChange={e=>setCode(e.target.value)}
                placeholder="PAIRING CODE"
                className="w-full bg-black/20 border border-white/10 rounded-lg p-3 text-white text-center tracking-[0.5em] font-mono focus:border-yellow-500/50 outline-none transition-all"
                maxLength={6}
            />
            <button
                onClick={() => onPair(ip, port, code)}
                className="w-full bg-yellow-600/20 text-yellow-400 border border-yellow-600/50 p-3 rounded-lg text-sm font-bold flex items-center justify-center gap-2 hover:bg-yellow-600/30 transition-all"
            >
                <Zap size={16}/> PAIR DEVICE
            </button>
        </div>

        {/* Connect Button - The "Smart" Button */}
        <button
            onClick={() => onConnect(ip, port)}
            className="w-full p-4 rounded-lg font-bold text-white bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 shadow-lg shadow-cyan-900/20 flex items-center justify-center gap-2 transition-all active:scale-95"
        >
            <ArrowRight size={18}/> CONNECT TO SHELL
        </button>

      </GlassCard>

      <p className="text-center text-[10px] text-gray-500 max-w-xs mx-auto">
          Use 'Auto-Detect' first. Then enter pairing code. Finally click Connect (Auto-switches to correct port).
      </p>
    </div>
  );
};