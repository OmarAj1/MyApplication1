import React, { useState, useEffect, useMemo } from 'react';
import {
  Shield, Trash2, Power, Smartphone, Wifi, Zap, Brain, User as UserIcon,
  Target, TrendingUp, DownloadCloud, Filter, Search, AlertTriangle, LogOut,
  Key, ArrowRight, RotateCcw, ClipboardCopy, List, History as HistoryIcon,
  Layers, Package, Globe, Activity // Added Globe and Activity
} from 'lucide-react';

const isNative = () => typeof (window as any).AndroidNative !== 'undefined';

const triggerHaptic = (type: 'light' | 'heavy' | 'success' = 'light') => {
  if (isNative()) (window as any).AndroidNative.hapticFeedback(type);
};

const showToast = (msg: string) => {
  if (isNative()) (window as any).AndroidNative.showToast(msg);
  else console.log(`[Toast] ${msg}`);
};

interface AppData {
  name: string;
  pkg: string;
  type: 'System' | 'User';
  status: 'Enabled' | 'Disabled' | 'Uninstalled' | 'Unknown';
  iconBase64?: string;
  // New fields for filtering
  listCategory?: string; // e.g., 'Google', 'OEM', 'AOSP'
  safety?: 'Recommended' | 'Advanced' | 'Expert' | 'Unsafe' | 'Unknown';
}

interface ActionLog {
    timestamp: string;
    action: string;
    pkg: string;
    user: number;
}

const GlassCard = ({ children, className = "", borderColor = "white" }: any) => {
  const borderColors: any = {
    white: "border-white/[0.05]",
    purple: "border-purple-500/20 shadow-[0_0_30px_rgba(168,85,247,0.1)]",
    green: "border-green-500/10 shadow-[0_0_30px_rgba(74,222,128,0.05)]",
    red: "border-red-500/10 shadow-[0_0_30px_rgba(239,68,68,0.05)]",
    amber: "border-amber-500/10 shadow-[0_0_30px_rgba(245,158,11,0.05)]",
    cyan: "border-cyan-500/10 shadow-[0_0_30px_rgba(6,182,212,0.05)]"
  };
  return (
    <div className={`backdrop-blur-xl bg-[#0f172a]/80 rounded-xl p-5 border ${borderColors[borderColor] || borderColors.white} shadow-2xl ${className}`}>
      {children}
    </div>
  );
};

const Badge = ({ text }: { text: string }) => {
  const styles: any = {
    System: "bg-purple-500/20 text-purple-300 border-purple-500/30",
    User: "bg-blue-500/20 text-blue-300 border-blue-500/30",
    Enabled: "bg-green-500/20 text-green-300 border-green-500/30",
    Disabled: "bg-amber-500/20 text-amber-300 border-amber-500/30",
    Uninstalled: "bg-red-500/20 text-red-300 border-red-500/30",
    Unknown: "bg-gray-500/20 text-gray-400 border-gray-500/30",

    // Safety
    Recommended: "bg-green-500/10 text-green-400 border-green-500/20",
    Advanced: "bg-amber-500/10 text-amber-400 border-amber-500/20",
    Expert: "bg-red-500/10 text-red-400 border-red-500/20",
    Unsafe: "bg-red-700/20 text-red-500 border-red-700/30"
  };
  return (
    <span className={`text-[9px] uppercase font-bold px-1.5 py-0.5 rounded border ${styles[text] || styles.Unknown}`}>
      {text}
    </span>
  );
};

const NeonButton = ({ onClick, active, icon: Icon, label, color = "green", loading = false, small=false }: any) => {
  const colors: any = {
    green: "from-green-500/90 to-emerald-600/90 shadow-green-500/20",
    cyan: "from-cyan-500/90 to-blue-600/90 shadow-cyan-500/20",
    red: "from-red-500/90 to-rose-600/90 shadow-red-500/20",
    gray: "from-gray-700/90 to-gray-800/90 shadow-gray-500/10"
  };
  return (
    <button
      onClick={(e) => { triggerHaptic('light'); onClick(e); }}
      disabled={loading}
      className={`relative overflow-hidden group ${small ? 'p-2' : 'w-full py-4 px-4'} text-sm rounded-xl font-medium tracking-wide transition-all duration-300 transform active:scale-[0.98]
        ${active ? `bg-gradient-to-r ${colors[color]} shadow-lg text-white` : 'bg-white/[0.03] hover:bg-white/[0.08] border border-white/[0.08] text-gray-400'}
        ${loading ? 'opacity-80 cursor-wait' : ''}`}
    >
      <div className="flex items-center justify-center space-x-2 relative z-10">
        {loading ? (
          <div className="animate-spin w-4 h-4 border-2 border-white/30 border-t-white rounded-full" />
        ) : (
          <>
            {Icon && <Icon size={small ? 16 : 18} strokeWidth={2} className={active ? "text-white" : "opacity-70"} />}
            {!small && <span>{label}</span>}
          </>
        )}
      </div>
    </button>
  );
};

const TabBar = ({ active, onChange }: any) => (
  <div className="fixed bottom-0 left-0 right-0 z-50 pointer-events-none">
    <div className="absolute bottom-0 w-full h-48 bg-gradient-to-t from-[#020617] via-[#020617]/95 to-transparent" />
    <div className="pointer-events-auto relative flex justify-center pb-6 pt-2">
      <div className="flex items-center p-1.5 rounded-full bg-[#0f172a]/80 backdrop-blur-2xl border border-white/[0.1] shadow-2xl">
        {[{id:'purge',icon:Smartphone},{id:'shield',icon:Shield},{id:'history',icon:HistoryIcon},{id:'user',icon:UserIcon}].map((tab) => {
          const isActive = active === tab.id;
          return (
            <button key={tab.id} onClick={() => { triggerHaptic('light'); onChange(tab.id); }}
              className={`relative flex items-center justify-center rounded-full transition-all duration-300 border px-5 py-3.5 ${isActive ? 'bg-cyan-500/20 text-cyan-400 border-cyan-500/20' : 'text-gray-500 border-transparent'}`}>
              <tab.icon size={22} strokeWidth={isActive ? 2.5 : 2} />
            </button>
          );
        })}
      </div>
    </div>
  </div>
);

// --- CATEGORY IDENTIFIER ---
const identifyApp = (pkg: string, type: string): { list: string, safety: string } => {
    let list = 'Misc';

    // Vendor Identification
    if (pkg.includes('google') || pkg.includes('android.vending')) list = 'Google';
    else if (pkg.includes('facebook') || pkg.includes('fb')) list = 'Facebook';
    else if (pkg.includes('amazon')) list = 'Amazon';
    else if (pkg.includes('microsoft') || pkg.includes('skype') || pkg.includes('office')) list = 'Microsoft';
    else if (pkg.includes('samsung') || pkg.includes('xiaomi') || pkg.includes('huawei')) list = 'OEM';
    else if (pkg.includes('verizon') || pkg.includes('tmobile') || pkg.includes('att')) list = 'Carrier';
    else if (pkg.startsWith('com.android')) list = 'AOSP';

    // Safety Identification
    let safety: any = 'Unknown';
    if (type === 'User') {
        safety = 'Recommended'; // User installed apps are safe to remove usually
    } else {
        if (list === 'AOSP' || list === 'OEM') safety = 'Expert'; // Core system stuff
        else if (list === 'Google') safety = 'Advanced'; // Can break features
        else if (list === 'Carrier' || list === 'Facebook' || list === 'Amazon') safety = 'Recommended'; // Bloat
        else safety = 'Advanced';
    }

    return { list, safety };
}

export default function App() {
  const [activeTab, setActiveTab] = useState('purge');
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin');
  const [authError, setAuthError] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  // Data State
  const [apps, setApps] = useState<AppData[]>([]);
  const [users, setUsers] = useState<{id: number, name: string}[]>([{id: 0, name: 'Owner'}]);
  const [currentUser, setCurrentUser] = useState(0);
  const [history, setHistory] = useState<ActionLog[]>([]);

  // Filter States
  const [listFilter, setListFilter] = useState('All'); // User, Google, OEM...
  const [safetyFilter, setSafetyFilter] = useState('All'); // Recommended, Advanced...
  const [stateFilter, setStateFilter] = useState('All'); // Enabled, Disabled, Uninstalled
  const [search, setSearch] = useState('');

  // Shield State
  const [vpnActive, setVpnActive] = useState(false);
  const [connectionTime, setConnectionTime] = useState(0);

  // Connection State
  const [pairIp, setPairIp] = useState('');
  const [pairPort, setPairPort] = useState('');
  const [connectIp, setConnectIp] = useState('');
  const [connectPort, setConnectPort] = useState('');
  const [code, setCode] = useState('');
  const [status, setStatus] = useState('Initializing...');
  const [purgeTabState, setPurgeTabState] = useState<'connect' | 'apps'>('connect');
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedApp, setSelectedApp] = useState<AppData | null>(null);
  const [actionType, setActionType] = useState<'uninstall' | 'disable' | 'restore' | 'enable' | null>(null);
  const [isLoadingApps, setIsLoadingApps] = useState(false);
  const [coreVersion, setCoreVersion] = useState("5.1.0-COMPLETE");

  // Shield: Poll status
  useEffect(() => {
    const checkStatus = () => {
      if (isNative() && (window as any).AndroidNative.getVpnStatus) {
        setVpnActive((window as any).AndroidNative.getVpnStatus());
      }
    };
    checkStatus();
    const interval = setInterval(checkStatus, 2000);
    return () => clearInterval(interval);
  }, []);

  // Shield: Connection timer
  useEffect(() => {
    let timer: any;
    if (vpnActive) {
      timer = setInterval(() => setConnectionTime(prev => prev + 1), 1000);
    } else {
      setConnectionTime(0);
    }
    return () => clearInterval(timer);
  }, [vpnActive]);

  useEffect(() => {
       (window as any).receiveAppList = (base64Json: string) => {
         setIsLoadingApps(false);
         try {
           const json = JSON.parse(atob(base64Json));
           const processed = json.map((app: any) => {
               const { list, safety } = identifyApp(app.pkg, app.type);
               return { ...app, listCategory: list, safety: safety };
           });
           setApps(processed);
           setPurgeTabState('apps');
           setStatus("Shell Active");
           triggerHaptic('success');
         } catch (e) {
           setPurgeTabState('apps');
         }
       };

       (window as any).receiveUsers = (base64Str: string) => {
           try {
               const raw = atob(base64Str);
               const matches = raw.match(/UserInfo\{(\d+):([^:]+):/g);
               if (matches) {
                   const parsedUsers = matches.map(m => {
                       const parts = m.match(/UserInfo\{(\d+):([^:]+):/);
                       return parts ? { id: parseInt(parts[1]), name: parts[2] } : null;
                   }).filter(Boolean);
                   setUsers(parsedUsers as any);
               }
           } catch(e) {}
       };

     (window as any).onPairingServiceFound = (ip: string, port: any) => { setPairIp(ip); setPairPort(port.toString()); setStatus('Pairing Info Found'); triggerHaptic('success'); };
     (window as any).onConnectServiceFound = (ip: string, port: any) => { setConnectIp(ip); setConnectPort(port.toString()); setStatus('Ready to Connect'); triggerHaptic('success'); };
     (window as any).adbStatus = (newStatus: string) => {
          if (newStatus === 'Connected') {
              setStatus('Shell Active'); triggerHaptic('success'); setIsLoadingApps(true);
              if (isNative()) {
                  (window as any).AndroidNative.getInstalledPackages();
                  (window as any).AndroidNative.getUsers();
              }
          } else { setStatus(newStatus); setIsLoadingApps(false); }
      };

     if (isNative()) {
         (window as any).AndroidNative.startMdnsDiscovery();
         setCoreVersion((window as any).AndroidNative.getNativeCoreVersion());
     }
     return () => { if (isNative()) (window as any).AndroidNative.stopMdnsDiscovery(); };
  }, []);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const toggleVpn = () => {
    const newState = !vpnActive;
    setVpnActive(newState);

    if (isNative() && (window as any).AndroidNative.startVpn) {
      if (newState) {
        (window as any).AndroidNative.startVpn();
        showToast("Initializing Shield Protocol...");
      } else {
        (window as any).AndroidNative.stopVpn();
        showToast("Shield Deactivated");
      }
    } else {
        // Fallback for simulation
        showToast(newState ? "Shield Activated (Simulated)" : "Shield Deactivated");
    }
  };

  const handleLogin = async () => {
    setIsAuthenticating(true); setAuthError('');
    await new Promise(r => setTimeout(r, 500));
    if (username === 'admin' && password === 'admin') setIsLoggedIn(true);
    else { setAuthError('Invalid credentials'); triggerHaptic('heavy'); }
    setIsAuthenticating(false);
  };

  const handleRetrieve = () => isNative() ? (window as any).AndroidNative.retrieveConnectionInfo() : showToast("Simulated");
  const handlePair = () => isNative() && (window as any).AndroidNative.pairAdb(pairIp, pairPort, code);
  const handleConnect = () => { setStatus("Connecting..."); setIsLoadingApps(true); if (isNative()) (window as any).AndroidNative.connectAdb(connectIp || pairIp, connectPort); };

  const handleConfirmAction = () => {
    if (selectedApp && actionType && isNative()) {
        (window as any).AndroidNative.executeCommand(actionType, selectedApp.pkg, currentUser);

        setHistory(prev => [{
            timestamp: new Date().toLocaleTimeString(),
            action: actionType || '',
            pkg: selectedApp.pkg,
            user: currentUser
        }, ...prev]);

        setApps(prev => prev.map(a => a.pkg === selectedApp.pkg ? {
            ...a,
            status: actionType === 'disable' ? 'Disabled' : (actionType === 'uninstall' ? 'Uninstalled' : 'Enabled')
        } : a));
        setModalOpen(false);
    }
  };

  const openModal = (app: AppData, action: any) => { setSelectedApp(app); setActionType(action); setModalOpen(true); triggerHaptic('light'); };

  const exportHistory = () => {
      const text = history.map(h => `[${h.timestamp}] User ${h.user}: ${h.action} -> ${h.pkg}`).join('\n');
      if (isNative()) (window as any).AndroidNative.shareText("UAD Export", text);
      else navigator.clipboard.writeText(text).then(() => showToast("Copied to clipboard"));
  };

  const renderLogin = () => (
    <div className="w-full max-w-sm mx-auto flex flex-col justify-center h-screen p-8">
        <GlassCard borderColor="cyan">
            <h2 className="text-2xl font-bold text-center text-white mb-2">NEXUS CORE</h2>
            <input type="text" placeholder="Username" value={username} onChange={e => setUsername(e.target.value)} className="bg-slate-800/60 border border-white/10 rounded-lg w-full p-3 mb-4 text-white focus:outline-none focus:border-cyan-500" />
            <input type="password" placeholder="Password" value={password} onChange={e => setPassword(e.target.value)} className="bg-slate-800/60 border border-white/10 rounded-lg w-full p-3 mb-6 text-white focus:outline-none focus:border-cyan-500" />
            <NeonButton label="Authenticate" color="cyan" icon={Key} onClick={handleLogin} loading={isAuthenticating} />
        </GlassCard>
    </div>
  );

  const renderPurgeConnection = () => (
    <div className="flex flex-col h-full justify-center space-y-6 animate-in fade-in duration-500">
      <GlassCard className="space-y-4" borderColor="cyan">
        <div className="flex justify-between items-center pb-2 border-b border-white/10">
            <span className="text-xs font-mono text-gray-400">STATUS</span>
            <span className={`text-xs font-bold ${status.includes('Found') || status.includes('Ready') || status.includes('Active') ? 'text-green-400' : 'text-amber-400'}`}>{status.toUpperCase()}</span>
        </div>
        <div className="space-y-2">
            <div className="flex gap-2">
                <input value={pairIp} onChange={e=>setPairIp(e.target.value)} placeholder="IP Address" className="flex-1 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono" />
                <input value={pairPort} onChange={e=>setPairPort(e.target.value)} placeholder="Port" className="w-20 bg-black/20 border border-white/10 rounded-lg p-3 text-white text-sm focus:border-cyan-500 outline-none font-mono text-center" />
            </div>
            <button onClick={handleRetrieve} className="w-full bg-white/5 hover:bg-cyan-500/20 text-cyan-400 p-2 rounded-lg border border-white/10 flex items-center justify-center gap-2 transition-all font-bold text-xs uppercase tracking-wider"><DownloadCloud size={16} /> Retrieve IP & Port</button>
        </div>
        <div className="space-y-2">
            <input value={code} onChange={e=>setCode(e.target.value)} placeholder="PAIRING CODE" className="w-full bg-black/20 border border-white/10 rounded-lg p-3 text-white text-center tracking-[0.5em] font-mono focus:border-cyan-500 outline-none" maxLength={6} inputMode="numeric" />
            <button onClick={handlePair} className="w-full bg-cyan-600 hover:bg-cyan-500 text-white p-3 rounded-lg text-sm font-bold transition-all mt-2 flex items-center justify-center gap-2"><Zap size={16} fill="currentColor"/> PAIR DEVICE</button>
        </div>
        <div className={`space-y-2 pt-2 transition-all duration-500 ${connectPort ? 'opacity-100' : 'opacity-60'}`}>
             <button onClick={handleConnect} className={`w-full p-3 rounded-lg font-bold text-white shadow-lg active:scale-[0.98] transition-all flex items-center justify-center gap-2 ${connectPort ? 'bg-gradient-to-r from-blue-600 to-indigo-600' : 'bg-white/10 text-gray-400'}`}>
                {isLoadingApps ? <div className="animate-spin w-4 h-4 border-2 border-white rounded-full border-t-transparent" /> : <ArrowRight size={16}/>}
                {isLoadingApps ? " CONNECTING..." : " CONNECT TO SHELL"}
            </button>
        </div>
      </GlassCard>
    </div>
  );

  const renderAppManager = () => {
      // --- FILTERING LOGIC ---
      const filteredApps = apps.filter(app => {
          // List Filter
          if (listFilter !== 'All') {
              if (listFilter === 'User' && app.type !== 'User') return false; // Strict User check
              if (listFilter !== 'User' && app.listCategory !== listFilter) return false;
          }
          // Safety Filter
          if (safetyFilter !== 'All' && app.safety !== safetyFilter) return false;
          // State Filter
          if (stateFilter !== 'All' && app.status !== stateFilter) return false;
          // Search
          return app.name.toLowerCase().includes(search.toLowerCase()) || app.pkg.toLowerCase().includes(search.toLowerCase());
      });

      return (
        <div className="flex flex-col h-full pt-2 animate-in slide-in-from-right-10 duration-300 min-h-screen">

          {/* SEARCH BAR & DISCONNECT */}
          <div className="flex gap-2 mb-2">
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search packages..." className="flex-1 bg-[#1e293b] border border-white/10 rounded-lg py-2 px-4 text-sm text-white focus:outline-none" />
              <button onClick={() => setPurgeTabState('connect')} className="bg-red-500/10 text-red-400 p-2 rounded-lg"><LogOut size={18} /></button>
          </div>

          {/* MAIN FILTERS (Safety / State / User) */}
          <div className="flex gap-2 mb-4 overflow-x-auto pb-2 scrollbar-hide">
              <select value={currentUser} onChange={e => setCurrentUser(parseInt(e.target.value))} className="bg-[#1e293b] border border-white/10 rounded-lg text-white text-xs px-2 py-2 outline-none">
                  {users.map(u => <option key={u.id} value={u.id}>User {u.id} ({u.name})</option>)}
              </select>
              <select value={safetyFilter} onChange={e => setSafetyFilter(e.target.value)} className="bg-[#1e293b] border border-white/10 rounded-lg text-white text-xs px-2 py-2 outline-none">
                  {['All', 'Recommended', 'Advanced', 'Expert', 'Unsafe'].map(f => <option key={f} value={f}>{f}</option>)}
              </select>
              <select value={stateFilter} onChange={e => setStateFilter(e.target.value)} className="bg-[#1e293b] border border-white/10 rounded-lg text-white text-xs px-2 py-2 outline-none">
                  {['All', 'Enabled', 'Disabled', 'Uninstalled'].map(f => <option key={f} value={f}>{f}</option>)}
              </select>
          </div>

          <div className="flex flex-1 overflow-hidden pb-20">
              {/* SIDEBAR (List Categories) */}
              <div className="w-24 flex flex-col gap-1 pr-2 border-r border-white/5 overflow-y-auto shrink-0">
                 {['All', 'User', 'Google', 'OEM', 'AOSP', 'Carrier', 'Facebook', 'Amazon', 'Microsoft', 'Misc'].map(f => (
                     <button key={f} onClick={() => setListFilter(f)} className={`text-left px-3 py-2 rounded-lg text-xs font-medium transition-colors ${listFilter === f ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/20' : 'text-gray-500 hover:bg-white/5'}`}>{f}</button>
                 ))}
                 <div className="mt-auto pt-4 text-[10px] text-gray-600 font-mono text-center">{filteredApps.length} APPS</div>
              </div>

              {/* APP LIST */}
              <div className="flex-1 pl-2 overflow-y-auto space-y-2">
                {filteredApps.map((app) => {
                    const iconSrc = app.iconBase64 ? `data:image/png;base64,${app.iconBase64}` : null;
                    return (
                        <div key={app.pkg} className="bg-[#1e293b]/40 border border-white/[0.05] rounded-lg p-3 transition-colors hover:bg-[#1e293b]/60">
                            <div className="flex justify-between items-start mb-2">
                                <div className="flex items-center overflow-hidden">
                                    {iconSrc ? <img src={iconSrc} alt="icon" className="w-8 h-8 rounded-md mr-3 bg-transparent" />
                                             : <div className="w-8 h-8 rounded-md mr-3 bg-gray-600/50 flex items-center justify-center"><Smartphone size={16} className="text-gray-400" /></div>}
                                    <div className="overflow-hidden pr-2">
                                        <h3 className="font-semibold text-white text-sm truncate">{app.name}</h3>
                                        <p className="text-[10px] text-gray-500 font-mono truncate">{app.pkg}</p>
                                    </div>
                                </div>
                                <div className="flex flex-col items-end gap-1">
                                    <Badge text={app.listCategory || 'Unknown'} />
                                    <Badge text={app.status} />
                                    {app.safety && <Badge text={app.safety} />}
                                </div>
                            </div>
                            <div className="flex gap-2 justify-end pt-2 border-t border-white/5">
                                {app.status === 'Disabled' ? <button onClick={() => openModal(app, 'enable')} className="p-1.5 bg-green-500/10 text-green-400 rounded"><Zap size={14}/></button>
                                : <button onClick={() => openModal(app, 'disable')} className="p-1.5 bg-amber-500/10 text-amber-400 rounded"><Power size={14}/></button>}
                                <button onClick={() => openModal(app, 'uninstall')} className="p-1.5 bg-red-500/10 text-red-400 rounded"><Trash2 size={14}/></button>
                                <button onClick={() => openModal(app, 'restore')} className="p-1.5 bg-blue-500/10 text-blue-400 rounded"><RotateCcw size={14}/></button>
                            </div>
                        </div>
                    );
                })}
              </div>
          </div>
        </div>
      );
  };

  const renderHistory = () => (
      <div className="space-y-4 animate-in fade-in">
          <GlassCard borderColor="purple">
              <div className="flex justify-between items-center mb-4">
                  <h2 className="text-xl font-bold text-white">Action History</h2>
                  <button onClick={exportHistory} className="bg-purple-500/20 text-purple-300 p-2 rounded-lg flex gap-2 items-center text-xs"><ClipboardCopy size={14}/> Export</button>
              </div>
              <div className="space-y-2 h-[60vh] overflow-y-auto">
                  {history.length === 0 ? <p className="text-gray-500 text-center text-sm">No actions recorded yet.</p> :
                   history.map((h, i) => (
                      <div key={i} className="bg-white/5 p-3 rounded-lg flex justify-between items-center">
                          <div>
                              <p className="text-white text-sm font-mono">{h.pkg}</p>
                              <p className="text-[10px] text-gray-400">{h.timestamp} • User {h.user}</p>
                          </div>
                          <Badge text={h.action} />
                      </div>
                  ))}
              </div>
          </GlassCard>
      </div>
  );

  const renderShield = () => (
    <div className="flex flex-col h-full items-center justify-between py-6 animate-in fade-in duration-500">

      {/* Header Status */}
      <GlassCard className="w-full flex items-center justify-between mb-8" borderColor={vpnActive ? "cyan" : "white"}>
        <div className="flex items-center space-x-3">
          <div className={`w-3 h-3 rounded-full ${vpnActive ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`} />
          <span className="text-gray-300 font-mono text-sm">
            {vpnActive ? 'ENCRYPTED' : 'UNPROTECTED'}
          </span>
        </div>
        <div className="text-cyan-400 font-mono text-sm">
          {vpnActive ? formatTime(connectionTime) : '--:--'}
        </div>
      </GlassCard>

      {/* Main Visual */}
      <div className="relative flex items-center justify-center w-72 h-72 my-8">
        <div className={`absolute inset-0 rounded-full transition-all duration-1000 ${vpnActive ? 'bg-cyan-500/10 blur-3xl' : 'bg-transparent'}`} />
        <div className={`absolute inset-0 rounded-full border-2 border-dashed transition-all duration-1000 ${vpnActive ? 'border-cyan-500/30 animate-[spin_8s_linear_infinite]' : 'border-gray-700'}`} />
        <div className={`absolute inset-4 rounded-full border border-dashed transition-all duration-1000 ${vpnActive ? 'border-cyan-400/20 animate-[spin_12s_linear_infinite_reverse]' : 'border-gray-800'}`} />

        <div className={`
          relative z-10 w-40 h-40 rounded-full flex items-center justify-center transition-all duration-500
          ${vpnActive ? 'bg-gradient-to-b from-cyan-900/50 to-cyan-800/20 shadow-[0_0_50px_rgba(6,182,212,0.3)]' : 'bg-gray-800/50'}
        `}>
          <Shield
            size={80}
            strokeWidth={1.5}
            className={`transition-all duration-500 ${vpnActive ? 'text-cyan-400 drop-shadow-[0_0_15px_rgba(6,182,212,0.8)]' : 'text-gray-600'}`}
          />
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-2 gap-4 w-full mb-8">
        <GlassCard className="flex flex-col items-center justify-center py-4 space-y-2">
          <Globe size={20} className="text-cyan-400" />
          <span className="text-xs text-gray-500 uppercase tracking-widest">Region</span>
          <span className="text-gray-200 font-bold">{vpnActive ? 'Nexus-1' : 'Unknown'}</span>
        </GlassCard>
        <GlassCard className="flex flex-col items-center justify-center py-4 space-y-2">
          <Wifi size={20} className="text-cyan-400" />
          <span className="text-xs text-gray-500 uppercase tracking-widest">Protocol</span>
          <span className="text-gray-200 font-bold">ADB-SEC</span>
        </GlassCard>
      </div>

      {/* Action Button */}
      <div className="w-full mt-auto">
        <NeonButton
          onClick={toggleVpn}
          active={vpnActive}
          icon={Power}
          label={vpnActive ? "DEACTIVATE SHIELD" : "ACTIVATE SHIELD"}
          color={vpnActive ? "cyan" : "gray"}
        />
      </div>
    </div>
  );

  const renderModal = () => {
    if (!modalOpen || !selectedApp || !actionType) return null;
    const config: any = { uninstall: { color: 'red' }, disable: { color: 'amber' }, enable: { color: 'green' }, restore: { color: 'blue' } };
    const info = config[actionType];
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/80 backdrop-blur-sm">
        <div className="bg-[#0f172a] border border-white/10 rounded-2xl p-6 w-full max-w-sm">
           <h3 className="text-lg font-bold text-white mb-4 capitalize">{actionType} Package?</h3>
           <p className="text-gray-400 text-sm mb-4">Target: {selectedApp.pkg}</p>
           <div className="flex gap-3">
               <button onClick={() => setModalOpen(false)} className="flex-1 py-3 rounded-xl bg-white/5 text-gray-300">Cancel</button>
               <button onClick={handleConfirmAction} className={`flex-1 py-3 rounded-xl font-bold bg-${info.color}-600 text-white`}>Confirm</button>
           </div>
        </div>
      </div>
    );
  };

  if (!isLoggedIn) return <div className="w-full bg-slate-900 text-white min-h-screen">{renderLogin()}</div>;

  return (
    <div className="bg-[#020617] text-white min-h-screen font-sans select-none">
      <main className="p-6 pb-48">
        {activeTab === 'purge' && (purgeTabState === 'connect' ? renderPurgeConnection() : renderAppManager())}
        {activeTab === 'shield' && renderShield()}
        {activeTab === 'history' && renderHistory()}
        {activeTab === 'user' && (
            <div className="space-y-6">
                <GlassCard borderColor="amber">
                    <div className="flex items-center space-x-4">
                        <div className="w-20 h-20 rounded-full bg-gradient-to-br from-amber-500/30 to-orange-500/30 flex items-center justify-center"><UserIcon size={40} className="text-amber-300" /></div>
                        <div><h2 className="text-2xl font-bold text-white">Operative: {username}</h2><p className="text-sm text-amber-300/60">Privacy Warlord</p></div>
                    </div>
                    <div className="mt-4"><NeonButton onClick={() => setIsLoggedIn(false)} label="Log Out" icon={LogOut} color="gray" size="sm" /></div>
                </GlassCard>
            </div>
        )}
      </main>
      <TabBar active={activeTab} onChange={setActiveTab} />
      {renderModal()}
    </div>
  );
}