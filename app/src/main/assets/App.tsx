import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  Shield, Trash2, Power, Wifi, Zap,
  Search, AlertTriangle, Filter, RotateCcw,
  ArrowRight, DownloadCloud
} from 'lucide-react';

// --- Native Interface Helpers ---
const isNative = () => typeof (window as any).AndroidNative !== 'undefined';

const Native = {
  toast: (msg: string) => isNative() && (window as any).AndroidNative.showToast(msg),
  haptic: () => isNative() && (window as any).AndroidNative.hapticFeedback('light'),
  pair: (ip: string, port: string, code: string) => isNative() && (window as any).AndroidNative.pairAdb(ip, port, code),
  connect: (ip: string, port: string) => isNative() && (window as any).AndroidNative.connectAdb(ip, port),
  execute: (cmd: string, pkg: string) => isNative() && (window as any).AndroidNative.executeCommand(cmd, pkg),
  startDiscovery: () => isNative() && (window as any).AndroidNative.startMdnsDiscovery(),
  stopDiscovery: () => isNative() && (window as any).AndroidNative.stopMdnsDiscovery(),
  retrieve: () => isNative() && (window as any).AndroidNative.retrieveConnectionInfo()
};

// --- Types ---
interface AppData {
  name: string;
  pkg: string;
  type: 'System' | 'User';
  status: 'Enabled' | 'Disabled' | 'Unknown';
}

// --- Components ---
const GlassCard = ({ children, className = "" }: any) => (
  <div className={`backdrop-blur-xl bg-[#0f172a]/80 rounded-xl p-5 border border-white/[0.08] shadow-2xl ${className}`}>
    {children}
  </div>
);

const Badge = ({ type, text }: { type: string, text: string }) => {
  const styles: any = {
    System: "bg-purple-500/20 text-purple-300 border-purple-500/30",
    User: "bg-blue-500/20 text-blue-300 border-blue-500/30",
    Enabled: "bg-green-500/20 text-green-300 border-green-500/30",
    Disabled: "bg-red-500/20 text-red-300 border-red-500/30",
    Unknown: "bg-gray-500/20 text-gray-400 border-gray-500/30"
  };
  return (
    <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded border ${styles[text] || styles.Unknown}`}>
      {text}
    </span>
  );
};

export default function App() {
  const [activeTab, setActiveTab] = useState('connect');
  const [apps, setApps] = useState<AppData[]>([]);
  const [filterType, setFilterType] = useState('All');
  const [search, setSearch] = useState('');

  // Connection State
  const [pairIp, setPairIp] = useState('');
  const [pairPort, setPairPort] = useState('');
  const [connectIp, setConnectIp] = useState('');
  const [connectPort, setConnectPort] = useState('');
  const [code, setCode] = useState('');
  const [status, setStatus] = useState('Initializing...');
  const [isScanning, setIsScanning] = useState(true);

  // Modal State
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedApp, setSelectedApp] = useState<AppData | null>(null);
  const [actionType, setActionType] = useState<'uninstall' | 'disable' | 'restore' | 'enable' | null>(null);

  const handleScan = useCallback(() => {
    setIsScanning(true);
    setStatus("Scanning...");
    Native.startDiscovery();
    setTimeout(() => setIsScanning(false), 10000);
  }, []);

  useEffect(() => {
    (window as any).receiveAppList = (json: any) => {
      setApps(json);
      setActiveTab('apps');
      Native.haptic();
      setStatus("Shell Active");
    };

    (window as any).onPairingServiceFound = (ip: string, port: number) => {
      setPairIp(ip);
      setPairPort(port.toString());
      setStatus('Pairing Service Found');
      Native.haptic();
    };

    (window as any).onConnectServiceFound = (ip: string, port: number) => {
      setConnectIp(ip);
      setConnectPort(port.toString());
      setStatus('Ready to Connect');
    };

    handleScan();
    return () => Native.stopDiscovery();
  }, [handleScan]);

  const filteredApps = useMemo(() => {
    let result = apps;
    if (filterType !== 'All') {
      if (filterType === 'Disabled') result = result.filter(a => a.status === 'Disabled');
      else if (filterType === 'Enabled') result = result.filter(a => a.status === 'Enabled');
      else result = result.filter(a => a.type === filterType);
    }
    const lower = search.toLowerCase();
    return result.filter(a => a.name.toLowerCase().includes(lower) || a.pkg.toLowerCase().includes(lower));
  }, [apps, filterType, search]);

  const handleRetrieve = () => {
      Native.haptic();
      Native.retrieve();
  };

  const handlePair = () => {
    if (!pairIp || !pairPort || !code) return Native.toast("All fields required");
    Native.pair(pairIp, pairPort, code);
  };

  const handleConnect = () => {
    const targetIp = connectIp || pairIp;
    const targetPort = connectPort;
    if (!targetPort) {
        Native.toast("Waiting for Connect Port...");
        return;
    }
    Native.connect(targetIp, targetPort);
  };

  const openModal = (app: AppData, action: 'uninstall' | 'disable' | 'restore' | 'enable') => {
    setSelectedApp(app);
    setActionType(action);
    setModalOpen(true);
    Native.haptic();
  };

  const confirmAction = () => {
    if (selectedApp && actionType) {
      Native.execute(actionType, selectedApp.pkg);
      setModalOpen(false);
      setSelectedApp(null);
    }
  };

  const renderConnection = () => (
    <div className="flex flex-col h-full justify-center space-y-6 animate-in fade-in duration-500">
      <div className="text-center space-y-4">
         <div className="w-20 h-20 bg-cyan-500/10 rounded-full flex items-center justify-center mx-auto ring-2 ring-cyan-500/20 shadow-[0_0_40px_rgba(6,182,212,0.2)]">
            <Wifi size={40} className="text-cyan-400" />
         </div>
         <h2 className="text-2xl font-bold text-white">Universal Android Debloater</h2>
         <p className="text-gray-400 text-sm max-w-xs mx-auto">
            1. Enable <b>Wireless Debugging</b>.<br/>
            2. Tap <b>Pair with Code</b>.<br/>
            3. Fill info & Connect.
         </p>
      </div>

      <GlassCard className="space-y-4">
        <div className="flex justify-between items-center pb-2 border-b border-white/10">
            <span className="text-xs font-mono text-gray-400">STATUS</span>
            <span className={`text-xs font-bold ${status.includes('Found') || status.includes('Ready') || status.includes('Active') ? 'text-green-400' : 'text-amber-400'}`}>
                {status.toUpperCase()}
            </span>
        </div>

        {/* --- MANUAL RETRIEVAL SECTION --- */}
        <div className="space-y-2">
            <label className="text-xs text-gray-500 font-bold uppercase">Manual Info</label>
            <div className="flex gap-2">
                <input
                    value={pairIp}
                    onChange={e=>setPairIp(e.target.value)}
                    placeholder="IP Address"
                    className="flex-1 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono"
                />
                <input
                    value={pairPort}
                    onChange={e=>setPairPort(e.target.value)}
                    placeholder="Port"
                    className="w-20 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono text-center"
                />
            </div>
            {/* UPDATED RETRIEVE BUTTON WITH TEXT */}
            <button
                onClick={handleRetrieve}
                className="w-full bg-white/5 hover:bg-cyan-500/20 text-cyan-400 p-2 rounded-lg border border-white/10 flex items-center justify-center gap-2 transition-all font-bold text-xs uppercase tracking-wider"
            >
                <DownloadCloud size={16} />
                Retrieve IP & Port
            </button>
        </div>

        {/* Pairing Code Section */}
        <div className="space-y-2">
            <input
                value={code}
                onChange={e=>setCode(e.target.value)}
                placeholder="PAIRING CODE"
                className="w-full bg-black/20 border border-white/10 rounded-lg p-3 text-white text-center tracking-[0.5em] font-mono focus:border-cyan-500 outline-none transition-colors placeholder:tracking-normal"
                maxLength={6}
                inputMode="numeric"
            />

            <button
                onClick={handlePair}
                className="w-full bg-cyan-600 hover:bg-cyan-500 text-white p-3 rounded-lg text-sm font-bold transition-all disabled:opacity-30 disabled:bg-gray-700 mt-2 flex items-center justify-center gap-2"
            >
                <Zap size={16} fill="currentColor"/> PAIR DEVICE
            </button>
        </div>

        {/* Connect Section */}
        <div className={`space-y-2 pt-2 transition-all duration-500 ${connectPort ? 'opacity-100' : 'opacity-60'}`}>
             <button
                onClick={handleConnect}
                className={`w-full p-3 rounded-lg font-bold text-white shadow-lg active:scale-[0.98] transition-all flex items-center justify-center gap-2
                    ${connectPort ? 'bg-gradient-to-r from-blue-600 to-indigo-600' : 'bg-white/10 text-gray-400'}`}
             >
                <ArrowRight size={16}/> {connectPort ? "CONNECT TO SHELL" : "Connect (Requires Port)"}
            </button>
        </div>
      </GlassCard>
    </div>
  );

  const renderAppManager = () => (
    <div className="flex flex-col h-full pt-2 animate-in slide-in-from-right-10 duration-300">
      <div className="flex gap-2 mb-4">
          <div className="relative flex-1">
              <Search className="absolute left-3 top-2.5 text-gray-500" size={16} />
              <input
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="Search packages..."
                className="w-full bg-[#1e293b] border border-white/10 rounded-lg py-2 pl-9 text-sm text-white focus:ring-1 focus:ring-cyan-500 outline-none"
              />
          </div>
          <button className="bg-[#1e293b] p-2 rounded-lg border border-white/10 text-gray-400">
             <Filter size={18} />
          </button>
      </div>

      <div className="flex flex-1 overflow-hidden">
          <div className="w-24 flex flex-col gap-1 pr-2 border-r border-white/5 overflow-y-auto">
             {['All', 'System', 'User', 'Enabled', 'Disabled'].map(f => (
                 <button
                    key={f}
                    onClick={() => { setFilterType(f); Native.haptic(); }}
                    className={`text-left px-3 py-2 rounded-lg text-xs font-medium transition-colors ${filterType === f ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/20' : 'text-gray-500 hover:bg-white/5'}`}
                 >
                    {f}
                 </button>
             ))}
             <div className="mt-auto pt-4 pb-2 text-[10px] text-gray-600 font-mono text-center">
                {apps.length} APPS
             </div>
          </div>

          <div className="flex-1 pl-2 overflow-y-auto space-y-2 pb-20">
            {filteredApps.length === 0 && <div className="text-center text-gray-500 text-sm mt-10">No packages found</div>}

            {filteredApps.map((app) => (
                <div key={app.pkg} className="bg-[#1e293b]/40 border border-white/[0.05] rounded-lg p-3 hover:bg-[#1e293b]/60 transition-colors group">
                    <div className="flex justify-between items-start mb-2">
                        <div className="overflow-hidden pr-2">
                            <h3 className="font-semibold text-white text-sm truncate">{app.name}</h3>
                            <p className="text-[10px] text-gray-500 font-mono truncate">{app.pkg}</p>
                        </div>
                        <div className="flex flex-col items-end gap-1 shrink-0">
                            <Badge type="type" text={app.type} />
                            <Badge type="status" text={app.status} />
                        </div>
                    </div>

                    <div className="flex gap-2 mt-2 pt-2 border-t border-white/5 justify-end">
                        {app.status === 'Disabled' ? (
                             <button onClick={() => openModal(app, 'enable')} className="p-1.5 bg-green-500/10 text-green-400 rounded hover:bg-green-500/20" title="Enable">
                                <Zap size={14}/>
                             </button>
                        ) : (
                            <button onClick={() => openModal(app, 'disable')} className="p-1.5 bg-amber-500/10 text-amber-400 rounded hover:bg-amber-500/20" title="Disable">
                                <Power size={14}/>
                            </button>
                        )}
                        <button onClick={() => openModal(app, 'uninstall')} className="p-1.5 bg-red-500/10 text-red-400 rounded hover:bg-red-500/20" title="Uninstall">
                            <Trash2 size={14}/>
                        </button>
                        <button onClick={() => openModal(app, 'restore')} className="p-1.5 bg-blue-500/10 text-blue-400 rounded hover:bg-blue-500/20" title="Restore">
                             <RotateCcw size={14} />
                        </button>
                    </div>
                </div>
            ))}
          </div>
      </div>
    </div>
  );

  const renderModal = () => {
    if (!modalOpen || !selectedApp) return null;

    const config = {
      uninstall: { color: 'red', title: 'Uninstall Package', desc: 'Removes the package for user 0. Data is cleared.' },
      disable: { color: 'amber', title: 'Disable Package', desc: 'Freezes the app. Data preserved, hidden from launcher.' },
      enable: { color: 'green', title: 'Enable Package', desc: 'Re-enables the application.' },
      restore: { color: 'blue', title: 'Restore Package', desc: 'Reinstalls package for current user if uninstalled.' }
    };

    const info = config[actionType || 'disable'];

    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
        <div className="bg-[#0f172a] border border-white/10 rounded-2xl p-6 w-full max-w-sm shadow-2xl">
           <div className="flex items-center gap-3 mb-4">
               <AlertTriangle className={`text-${info.color}-400`} size={24} />
               <h3 className="text-lg font-bold text-white">{info.title}</h3>
           </div>

           <div className="bg-white/5 p-3 rounded-lg mb-4">
               <p className="font-mono text-xs text-cyan-300 mb-1">{selectedApp.pkg}</p>
               <p className="text-sm text-gray-300">{selectedApp.name}</p>
           </div>

           <p className="text-sm text-gray-400 mb-6">
             {info.desc}
           </p>

           <div className="flex gap-3">
               <button onClick={() => setModalOpen(false)} className="flex-1 py-3 rounded-xl bg-white/5 text-gray-300 font-medium hover:bg-white/10">
                  Cancel
               </button>
               <button onClick={confirmAction} className={`flex-1 py-3 rounded-xl font-bold bg-${info.color}-600 text-white shadow-lg`}>
                  Confirm
               </button>
           </div>
        </div>
      </div>
    );
  };

  return (
    <div className="bg-[#020617] min-h-screen text-white font-sans selection:bg-cyan-500/30 p-4 pb-20">
        {activeTab === 'connect' ? renderConnection() : renderAppManager()}
        {renderModal()}
    </div>
  );
}