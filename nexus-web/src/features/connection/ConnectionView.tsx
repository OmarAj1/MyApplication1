import React, { useState, useEffect } from 'react';
import { DownloadCloud, Zap, ArrowRight } from 'lucide-react';
import { GlassCard } from '../../components/ui/GlassCard';
import { NeonButton } from '../../components/ui/NeonButton';

// 1. UPDATE PROPS: Accept the new data and function
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

  // 2. AUTO-FILL: When data comes from Android, fill the inputs
//   useEffect(() => {
//       if (pairingData.ip) {
//           setIp(pairingData.ip);
//           setPort(pairingData.port);
//       } else if (connectData.ip) {
//           setIp(connectData.ip);
//           setPort(connectData.port);
//       }
//   }, [pairingData, connectData]);
// Listen for ANY data change and update the inputs immediately
  useEffect(() => {
      // 1. If we have a valid Connection Port, ALWAYS use it (Priority #1)
      if (connectData && connectData.port && connectData.port !== "" && connectData.port !== "0") {
          console.log("Auto-filling Connection Port:", connectData.port);
          setIp(connectData.ip);
          setPort(connectData.port);
      }
      // 2. Otherwise, if we have Pairing data AND we haven't typed anything yet, use that
      else if (pairingData && pairingData.port && (port === '' || port === '0')) {
          console.log("Auto-filling Pairing Port:", pairingData.port);
          setIp(pairingData.ip);
          setPort(pairingData.port);
      }
  }, [connectData, pairingData]); // Remove 'port' dependency to allow manual edits
  return (
    <div className="flex flex-col h-full justify-center space-y-6 animate-in fade-in duration-500">
      <GlassCard className="space-y-4" borderColor="cyan">
        <div className="flex justify-between items-center pb-2 border-b border-white/10">
            <span className="text-xs font-mono text-gray-400">STATUS</span>
            <span className="text-xs font-bold text-cyan-400">{status}</span>
        </div>
        <div className="space-y-2">
            <div className="flex gap-2">
                <input value={ip} onChange={e=>setIp(e.target.value)} placeholder="IP Address" className="flex-1 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono" />
                <input value={port} onChange={e=>setPort(e.target.value)} placeholder="Port" className="w-20 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono text-center" />
            </div>

            {/* 3. BUTTON: Hook up the onClick event */}
            <button
                onClick={onRetrieve}
                className="w-full bg-white/5 text-cyan-400 p-2 rounded-lg border border-white/10 flex items-center justify-center gap-2 text-xs uppercase hover:bg-white/10 transition-colors"
            >
                <DownloadCloud size={16} /> Retrieve IP
            </button>
        </div>
        <div className="space-y-2">
            <input value={code} onChange={e=>setCode(e.target.value)} placeholder="PAIRING CODE" className="w-full bg-black/20 border border-white/10 rounded-lg p-3 text-white text-center tracking-[0.5em] font-mono" maxLength={6} />
            <button onClick={() => onPair(ip, port, code)} className="w-full bg-cyan-600 text-white p-3 rounded-lg text-sm font-bold flex items-center justify-center gap-2"><Zap size={16}/> PAIR DEVICE</button>
        </div>
        <button onClick={() => onConnect(ip, port)} className="w-full p-3 rounded-lg font-bold text-white bg-gradient-to-r from-blue-600 to-indigo-600 flex items-center justify-center gap-2"><ArrowRight size={16}/> CONNECT TO SHELL</button>
      </GlassCard>
    </div>
  );
};