import React, { useState, useEffect, useRef } from 'react';
import { 
  Shield, 
  Trash2, 
  Activity, 
  Power, 
  Smartphone, 
  Wifi, 
  Terminal,
  Zap,
  Lock,
  Globe,
  Brain,
  Sparkles,
  CheckCircle,
  XCircle,
  ScanEye,
  AlertTriangle,
  Fingerprint,
  User as UserIcon,
  Trophy,
  Target,
  TrendingUp,
  Share2,
  Download,
  Plus,
  Minus,
  Info,
  Database,
  Clock,
  LayoutGrid,
  Cpu,
  LogOut,
  Key,
  ChevronRight
} from 'lucide-react';

// --- Native Interfaces ---

const isNative = () => typeof (window as any).AndroidNative !== 'undefined';

const triggerHaptic = (type: 'light' | 'heavy' | 'success' = 'light') => {
  if (isNative()) {
    (window as any).AndroidNative.hapticFeedback(type);
  }
};

const showToast = (msg: string) => {
  if (isNative()) {
    (window as any).AndroidNative.showToast(msg);
  } else {
    console.log(`[Toast] ${msg}`);
  }
};

const shareData = (title: string, content: string) => {
  if (isNative()) {
    (window as any).AndroidNative.shareText(title, content);
  } else {
    console.log(`[Share] ${title}:`, content);
    // Use a custom modal or just log in simulation to avoid alert() blocks in some envs
    console.log(`Simulated Export:\n${content.substring(0, 100)}...`);
  }
};

// --- C++ Bridge Access ---
const getCoreVersion = () => {
    if (isNative()) {
        return (window as any).AndroidNative.getNativeCoreVersion();
    }
    return "WEB_SIMULATION_v1.0";
}

const AdbModule = {
  pair: async (ip: string, port: string, code: string) => {
    console.log(`[Native] Pairing ${ip}:${port} code:${code}`);
    if (isNative()) {
       (window as any).AndroidNative.pairAdb(ip, port, code);
       return "Initiated";
    }
    await new Promise(r => setTimeout(r, 1500));
    return "Success";
  },
  connect: async (ip: string, port: string) => {
    if (isNative()) {
       const res = (window as any).AndroidNative.connectAdb(ip, port);
       return res ? "Connected" : "Failed";
    }
    await new Promise(r => setTimeout(r, 1000));
    return "Connected";
  },
  uninstall: async (pkg: string) => {
      if (isNative()) {
          (window as any).AndroidNative.uninstallPackage(pkg);
      }
      return true;
  },
  revokeInternet: async (pkg: string) => {
      if (isNative()) {
          (window as any).AndroidNative.revokeInternet(pkg);
      }
      return true;
  },
  getApps: async () => {
      if (isNative()) {
          (window as any).AndroidNative.getInstalledPackages();
      }
      return null;
  }
};

// --- Mock Data Pools ---
const AD_DOMAINS = [
  "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", "ads.facebook.com",
  "creative.ak.fbcdn.net", "adservice.google.com", "analytics.twitter.com",
  "app-measurement.com", "firebase-installations.googleapis.com"
];

const SAFE_DOMAINS = [
  "api.weather.com", "samsung-updates.com", "connectivitycheck.gstatic.com",
  "clients3.google.com", "update.googleapis.com", "outlook.office365.com",
  "imap.gmail.com", "api.spotify.com", "netflix.com"
];

interface CustomRule {
  domain: string;
  type: 'BLOCK' | 'ALLOW';
}

const ShieldModule = {
  startVpn: async () => {
    if (isNative()) {
        (window as any).AndroidNative.startVpn();
        return true;
    }
    return true;
  },
  stopVpn: async () => true,
  simulateTraffic: (customRules: CustomRule[]): { domain: string, blocked: boolean, ruleApplied?: string } => {
    const isBad = Math.random() > 0.4;
    const pool = isBad ? AD_DOMAINS : SAFE_DOMAINS;
    const domain = pool[Math.floor(Math.random() * pool.length)];

    const customRule = customRules.find(r => domain.includes(r.domain));
    if (customRule) {
      return { domain, blocked: customRule.type === 'BLOCK', ruleApplied: customRule.type };
    }

    return { domain, blocked: isBad };
  }
};

// --- Modern Components ---

const GlassCard = ({ children, className = "", highlight = false, borderColor = "white" }: any) => {
  const borderColors: any = {
    white: "border-white/[0.05]",
    purple: "border-purple-500/20 shadow-[0_0_30px_rgba(168,85,247,0.1)]",
    green: "border-green-500/10 shadow-[0_0_30px_rgba(74,222,128,0.05)]",
    red: "border-red-500/10 shadow-[0_0_30px_rgba(239,68,68,0.05)]",
    amber: "border-amber-500/10 shadow-[0_0_30px_rgba(245,158,11,0.05)]",
    cyan: "border-cyan-500/10 shadow-[0_0_30px_rgba(6,182,212,0.05)]"
  };

  return (
    <div className={`glass-card rounded-2xl p-5 transition-all duration-500 hover:bg-white/[0.05] border ${borderColors[borderColor] || borderColors.white} ${className}`}>
      {children}
    </div>
  );
};

const NeonButton = ({ onClick, active, icon: Icon, label, color = "green", loading = false, size = "md", fullWidth = true }: any) => {
  const colors: any = {
    green: "from-green-500/90 to-emerald-600/90 shadow-green-500/20",
    red: "from-red-500/90 to-rose-600/90 shadow-red-500/20",
    blue: "from-blue-500/90 to-indigo-600/90 shadow-blue-500/20",
    purple: "from-purple-500/90 to-violet-600/90 shadow-purple-500/20",
    amber: "from-amber-500/90 to-orange-600/90 shadow-amber-500/20",
    cyan: "from-cyan-500/90 to-blue-600/90 shadow-cyan-500/20",
    gray: "from-gray-700/90 to-gray-800/90 shadow-gray-500/10"
  };

  const pad = size === "sm" ? "py-2 px-3 text-xs" : "py-4 px-4 text-sm";
  const widthClass = fullWidth ? "w-full" : "w-auto";

  const handleClick = (e: any) => {
    triggerHaptic('light');
    onClick(e);
  };

  return (
    <button
      onClick={handleClick}
      disabled={loading}
      className={`relative overflow-hidden group ${widthClass} ${pad} rounded-xl font-medium tracking-wide transition-all duration-300 transform active:scale-[0.98]
        ${active
          ? `bg-gradient-to-r ${colors[color]} shadow-lg text-white ring-1 ring-white/20`
          : 'bg-white/[0.03] hover:bg-white/[0.08] border border-white/[0.08] text-gray-400 hover:text-white'}
        ${loading ? 'opacity-80 cursor-wait' : ''}`}
    >
      <div className="flex items-center justify-center space-x-2 relative z-10">
        {loading ? (
          <div className="animate-spin w-4 h-4 border-2 border-white/30 border-t-white rounded-full" />
        ) : (
          <>
            {Icon && <Icon size={size === "sm" ? 14 : 18} strokeWidth={2} className={active ? "text-white" : "opacity-70 group-hover:opacity-100"} />}
            <span>{label}</span>
          </>
        )}
      </div>
    </button>
  );
};

const TabBar = ({ active, onChange }: any) => {
    const handleTabChange = (id: string) => {
        triggerHaptic('light');
        onChange(id);
    };

    return (
      <div className="fixed bottom-0 left-0 right-0 z-50 pointer-events-none">
        <div className="absolute bottom-0 w-full h-48 bg-gradient-to-t from-[#020617] via-[#020617]/95 to-transparent" />

        <div className="pointer-events-auto relative flex justify-center pb-6 pt-2">
          <div className="flex items-center p-1.5 rounded-full bg-[#0f172a]/80 backdrop-blur-2xl border border-white/[0.1] shadow-2xl">
            {[
              { id: 'purge', icon: Smartphone, label: 'Purge' },
              { id: 'shield', icon: Shield, label: 'Shield' },
              { id: 'insights', icon: Brain, label: 'Insights' },
              { id: 'user', icon: UserIcon, label: 'Profile' }
            ].map((tab) => {
              const isActive = active === tab.id;

              let activeStyles = '';
              if (tab.id === 'purge') activeStyles = 'bg-emerald-500/20 text-emerald-400 border-emerald-500/20';
              if (tab.id === 'shield') activeStyles = 'bg-blue-500/20 text-blue-400 border-blue-500/20';
              if (tab.id === 'insights') activeStyles = 'bg-purple-500/20 text-purple-400 border-purple-500/20';
              if (tab.id === 'user') activeStyles = 'bg-amber-500/20 text-amber-400 border-amber-500/20';

              const inactiveStyles = 'text-gray-500 hover:text-gray-300 border-transparent';

              return (
                <button
                  key={tab.id}
                  onClick={() => handleTabChange(tab.id)}
                  className={`
                    relative flex items-center justify-center rounded-full transition-all duration-300
                    border px-5 py-3.5
                    ${isActive ? activeStyles : inactiveStyles}
                  `}
                >
                  <tab.icon
                    size={22}
                    strokeWidth={isActive ? 2.5 : 2}
                    className={`transition-transform duration-300 ${isActive ? 'scale-100' : 'scale-90 opacity-70'}`}
                  />
                </button>
              );
            })}
          </div>
        </div>
      </div>
    );
};

// --- Main Application ---

export default function App() {
  const [activeTab, setActiveTab] = useState<'purge' | 'shield' | 'insights' | 'user'>('purge');
  const [isConnected, setIsConnected] = useState(false);
  const [isPairing, setIsPairing] = useState(false);
  const [pairingCode, setPairingCode] = useState('');
  const [port, setPort] = useState('');
  const [ipAddress, setIpAddress] = useState('127.0.0.1');

  // Auth State
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin');
  const [authError, setAuthError] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  // Shield State
  const [vpnActive, setVpnActive] = useState(false);
  const [stats, setStats] = useState({ blocked: 124, saved: 4.2 });
  const [totalScanned, setTotalScanned] = useState(1240);
  const [trafficLog, setTrafficLog] = useState<string[]>([]);

  // Custom Rules State
  const [customRules, setCustomRules] = useState<CustomRule[]>([]);
  const [newRuleDomain, setNewRuleDomain] = useState('');
  const [newRuleType, setNewRuleType] = useState<'BLOCK' | 'ALLOW'>('BLOCK');

  // Analysis State
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [aiSuggestions, setAiSuggestions] = useState<any[]>([]);

  const [expandedApp, setExpandedApp] = useState<string | null>(null);
  const [apps, setApps] = useState([
    { name: 'Analytics Agent', pkg: 'com.tracking.agent', risk: 'high', category: 'System', permissions: ['READ_CONTACTS', 'ACCESS_FINE_LOCATION', 'READ_SMS'], dataUsage: '145 MB', lastUsed: '2 mins ago' },
    { name: 'Cloud Daemon', pkg: 'com.vendor.cloud', risk: 'medium', category: 'Utility', permissions: ['INTERNET', 'WAKE_LOCK'], dataUsage: '1.2 GB', lastUsed: 'Background' },
    { name: 'Market Push', pkg: 'com.ads.push', risk: 'high', category: 'Social', permissions: ['SYSTEM_ALERT_WINDOW', 'GET_ACCOUNTS'], dataUsage: '450 KB', lastUsed: '1 hour ago' },
    { name: 'System Updater', pkg: 'com.android.updater', risk: 'safe', category: 'System', permissions: ['WRITE_SECURE_SETTINGS'], dataUsage: '12 MB', lastUsed: 'Yesterday' },
    { name: 'Face Filters', pkg: 'com.cam.filters', risk: 'medium', category: 'Social', permissions: ['CAMERA', 'RECORD_AUDIO'], dataUsage: '89 MB', lastUsed: '5 mins ago' },
  ]);

  const categories = Array.from(new Set(apps.map(a => a.category)));
  const userLevel = Math.floor(stats.blocked / 50) + 1;
  const currentLevelProgress = stats.blocked % 50;
  const progressPercent = (currentLevelProgress / 50) * 100;
  const [coreVersion, setCoreVersion] = useState("Loading...");

  // --- Listen for Native Events ---
  useEffect(() => {
     (window as any).receiveAppList = (jsonString: string) => {
         try {
           const receivedApps = JSON.parse(jsonString);
           console.log("Apps received from Java:", receivedApps);
           // To update the UI with real apps, you would call setApps(receivedApps);
         } catch(e) {
             console.error("Failed to parse app list from Java", e);
         }
     };
     // Set Core Version on mount
     setCoreVersion(getCoreVersion());
  }, []);

  const getRankTitle = (lvl: number) => {
    if (lvl < 5) return "Net Initiate";
    if (lvl < 10) return "Cyber Sentinel";
    if (lvl < 20) return "Data Guardian";
    return "Privacy Warlord";
  };

  useEffect(() => {
    let interval: any;
    if (vpnActive) {
      interval = setInterval(() => {
        const event = ShieldModule.simulateTraffic(customRules);
        setTotalScanned(t => t + 1);

        if (event.blocked) {
          setStats(s => ({
            blocked: s.blocked + 1,
            saved: s.saved + 0.05
          }));
          setTrafficLog(prev => {
            const label = event.ruleApplied ? `${event.domain} [${event.ruleApplied}]` : event.domain;
            return [label, ...prev].slice(0, 50);
          });
          if (Math.random() > 0.8) triggerHaptic('light');
        }
      }, 1200);
    }
    return () => clearInterval(interval);
  }, [vpnActive, customRules]);

  const handlePair = async () => {
    setIsPairing(true);
    triggerHaptic('heavy');
    try {
      await AdbModule.pair(ipAddress, port, pairingCode);
      await new Promise(r => setTimeout(r, 2000));
      const status = await AdbModule.connect(ipAddress, port);

      if (status === "Connected") {
          setIsConnected(true);
          triggerHaptic('success');
          showToast('Wireless Debugging Connected');
          AdbModule.getApps();
      } else {
          showToast('Pairing Failed. Check Code/Port');
      }
    } catch(e) { console.error(e); }
    setIsPairing(false);
  };

  const handleAction = async (action: string, pkg: string) => {
    triggerHaptic('light');
    if (!isConnected) {
        showToast('Connect ADB first');
        return;
    }
    if (action === 'uninstall') {
      await AdbModule.uninstall(pkg);
      setApps(prev => prev.filter(a => a.pkg !== pkg));
      triggerHaptic('success');
    } else {
      await AdbModule.revokeInternet(pkg);
      showToast('Internet Revoked');
    }
  };

  const toggleVpn = () => {
      const newState = !vpnActive;
      setVpnActive(newState);
      triggerHaptic('heavy');
      if (newState) {
          ShieldModule.startVpn();
      } else {
          ShieldModule.stopVpn();
      }
  };

  const addCustomRule = () => {
    if (!newRuleDomain) return;
    setCustomRules(prev => [...prev, { domain: newRuleDomain, type: newRuleType }]);
    setNewRuleDomain('');
    showToast(`Rule added: ${newRuleType} ${newRuleDomain}`);
  };

  const removeCustomRule = (domain: string) => {
    setCustomRules(prev => prev.filter(r => r.domain !== domain));
  };

  const exportShieldLog = () => {
    const content = `NEXUS PRIME TRAFFIC LOG
Generated: ${new Date().toISOString()}

${trafficLog.join('\n')}`;
    shareData("Shield Traffic Log", content);
  };

  const exportAnalysis = () => {
     const content = `NEXUS PRIME ANALYSIS REPORT
Generated: ${new Date().toISOString()}

${JSON.stringify(aiSuggestions, null, 2)}`;
     shareData("AI Analysis Report", content);
  };

  const handleLogin = async () => {
    setIsAuthenticating(true);
    triggerHaptic('light');
    setAuthError('');
    await new Promise(r => setTimeout(r, 1000));

    if (username === 'admin' && password === 'admin') {
      setIsLoggedIn(true);
      triggerHaptic('success');
      showToast('Welcome back, Operative.');
    } else {
      setAuthError('Invalid credentials. Access Denied.');
      triggerHaptic('heavy');
    }
    setIsAuthenticating(false);
  };

  const handleLogout = () => {
    setIsLoggedIn(false);
    setUsername('');
    setPassword('');
    triggerHaptic('light');
  };

  const runTrafficAnalysis = async () => {
    setIsAnalyzing(true);
    triggerHaptic('light');
    setAiSuggestions([]);

    const logsToAnalyze = trafficLog.length > 3 ? trafficLog.map(l => l.split(' ')[0]) : [
      "graph.facebook.com", "google-analytics.com", "samsung-updates.com",
      "api.weather.com", "ads.twitter.com", "telemetry.nvidia.com"
    ];

    const HEURISTICS: Record<string, { type: 'SAFE' | 'TRACKER', reason: string }> = {
        'google-analytics.com': { type: 'TRACKER', reason: 'User behavior tracking and analytics.' },
        'graph.facebook.com': { type: 'TRACKER', reason: 'Social graph data collection.' },
        'ads.twitter.com': { type: 'TRACKER', reason: 'Targeted advertising delivery.' },
        'telemetry.nvidia.com': { type: 'TRACKER', reason: 'Hardware usage telemetry.' },
        'doubleclick.net': { type: 'TRACKER', reason: 'Cross-site advertising tracking.' },
        'samsung-updates.com': { type: 'SAFE', reason: 'Device firmware update service. Necessary for security.' },
        'api.weather.com': { type: 'SAFE', reason: 'Essential data for weather widgets.' },
        'connectivitycheck.gstatic.com': { type: 'SAFE', reason: 'Android OS network status check.' },
        'outlook.office365.com': { type: 'SAFE', reason: 'Email synchronization service.' }
    };

    await new Promise(r => setTimeout(r, 2000));

    const results = logsToAnalyze.map(domain => {
        let match = null;
        for (const [key, info] of Object.entries(HEURISTICS)) {
            if (domain.includes(key)) {
                match = info;
                break;
            }
        }
        return match ? { domain, ...match } : { domain, type: 'UNKNOWN', reason: 'No specific data on this domain. Review manually.' };
    });

    setAiSuggestions(results);
    setIsAnalyzing(false);
    triggerHaptic('success');
  };

  const renderLogin = () => (
    <div className="w-full max-w-sm mx-auto flex flex-col justify-center h-screen p-8">
        <div className="flex justify-center mb-8">
            <Shield size={64} className="text-cyan-400/80" strokeWidth={1} />
        </div>
        <GlassCard borderColor="cyan">
            <h2 className="text-2xl font-bold text-center text-white mb-2">NEXUS CORE</h2>
            <p className="text-center text-cyan-300/60 mb-6 text-sm">Secure Access Required</p>
            {authError && <div className="bg-red-500/20 border border-red-500/30 text-red-300 p-3 rounded-lg mb-4 text-xs">{authError}</div>}
            <input
                type="text"
                placeholder="Username"
                value={username}
                onChange={e => setUsername(e.target.value)}
                className="bg-slate-800/60 border border-white/10 rounded-lg w-full p-3 mb-4 text-white placeholder-gray-500 focus:ring-2 focus:ring-cyan-500 focus:outline-none"
            />
            <input
                type="password"
                placeholder="Password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                className="bg-slate-800/60 border border-white/10 rounded-lg w-full p-3 mb-6 text-white placeholder-gray-500 focus:ring-2 focus:ring-cyan-500 focus:outline-none"
            />
            <NeonButton
              label="Authenticate"
              color="cyan"
              icon={Key}
              onClick={handleLogin}
              loading={isAuthenticating}
            />
        </GlassCard>
    </div>
  );

  const renderInsights = () => (
    <div className="space-y-6">
        <GlassCard borderColor="purple">
          <div className="flex items-start justify-between">
            <div>
              <h3 className="text-lg font-bold text-white flex items-center">
                <Brain size={20} className="mr-2 text-purple-400"/> AI-Powered Insights
              </h3>
              <p className="text-purple-300/60 text-sm">Heuristic analysis of network traffic.</p>
            </div>
            <NeonButton
                label={isAnalyzing ? "Analyzing..." : "Run Analysis"}
                onClick={runTrafficAnalysis}
                icon={Sparkles}
                color="purple"
                loading={isAnalyzing}
                size="sm"
                fullWidth={false}
            />
          </div>

          <div className="mt-6 space-y-3">
             {aiSuggestions.length === 0 && !isAnalyzing && (
                <div className="text-center py-8">
                    <LayoutGrid size={40} className="mx-auto text-purple-400/20 mb-2"/>
                    <p className="text-purple-300/40">Run an analysis to generate suggestions.</p>
                </div>
             )}
             {isAnalyzing && (
                <div className="text-center py-8">
                    <Cpu size={40} className="mx-auto text-purple-400/20 mb-2 animate-pulse"/>
                    <p className="text-purple-300/40">Processing heuristics...</p>
                </div>
             )}
             {aiSuggestions.map((s, i) => (
                <div key={i} className="flex items-center justify-between bg-purple-500/5 p-3 rounded-lg">
                    <div className="flex items-center">
                        {s.type === 'TRACKER' && <XCircle size={18} className="text-red-400 mr-3"/>}
                        {s.type === 'SAFE' && <CheckCircle size={18} className="text-green-400 mr-3"/>}
                        {s.type === 'UNKNOWN' && <AlertTriangle size={18} className="text-amber-400 mr-3"/>}
                        <div>
                            <p className="font-mono text-sm text-white">{s.domain}</p>
                            <p className="text-xs text-purple-300/50">{s.reason}</p>
                        </div>
                    </div>
                    <button className="text-purple-400/60 hover:text-purple-300">
                        <Info size={16}/>
                    </button>
                </div>
             ))}
          </div>
           {aiSuggestions.length > 0 && <NeonButton label="Export Analysis" onClick={exportAnalysis} icon={Download} color="purple" fullWidth={true} />}
        </GlassCard>
    </div>
  );

  const renderShield = () => (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <GlassCard borderColor={vpnActive ? "green" : "red"}>
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-bold text-white">Shield Status</h3>
                <div className={`w-3 h-3 rounded-full ${vpnActive ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`}></div>
            </div>
            <p className="text-sm text-gray-400 mb-4">{vpnActive ? "Actively blocking threats" : "VPN is offline"}</p>
            <NeonButton
              label={vpnActive ? "Deactivate" : "Activate"}
              onClick={toggleVpn}
              icon={vpnActive ? Minus : Plus}
              color={vpnActive ? "red" : "green"}
            />
        </GlassCard>

        <GlassCard borderColor="green">
          <h3 className="text-lg font-bold text-white flex items-center"><Zap size={16} className="mr-2 text-green-400"/>Real-time Stats</h3>
          <p className="text-sm text-gray-400">Since last activation</p>
          <div className="mt-2 text-3xl font-bold text-white">{stats.blocked}</div>
          <p className="text-green-400 text-sm">Threats blocked</p>
        </GlassCard>
      </div>

      <GlassCard borderColor="blue">
        <div className="flex items-start justify-between">
          <div>
            <h3 className="text-lg font-bold text-white flex items-center"><ScanEye size={20} className="mr-2 text-blue-400"/>Traffic Log</h3>
            <p className="text-blue-300/60 text-sm">Live feed of blocked domains.</p>
          </div>
          <NeonButton label="Export Log" onClick={exportShieldLog} icon={Download} color="blue" size="sm" fullWidth={false} />
        </div>
        <div className="font-mono text-xs mt-4 h-48 overflow-y-auto bg-blue-950/30 p-3 rounded-lg border border-blue-500/20">
          {trafficLog.length === 0 ? (
            <div className="flex items-center justify-center h-full text-blue-300/40">
              <p>Activate Shield to start logging...</p>
            </div>
          ) : trafficLog.map((log, i) => (
            <p key={i} className={`whitespace-nowrap ${log.includes('[BLOCK]') ? 'text-amber-400' : 'text-blue-300'}`}>
              <span className="text-blue-500/50 mr-2">{`[${totalScanned - i}]`}</span>
              <XCircle size={10} className="inline mr-1 text-red-500/80"/>
              {log}
            </p>
          ))}
        </div>
      </GlassCard>

       <GlassCard borderColor="amber">
          <h3 className="text-lg font-bold text-white flex items-center"><Fingerprint size={20} className="mr-2 text-amber-400"/>Custom Rules</h3>
          <div className="flex items-center space-x-2 mt-4">
              <input
                  type="text"
                  placeholder="e.g., ad.company.com"
                  value={newRuleDomain}
                  onChange={(e) => setNewRuleDomain(e.target.value)}
                  className="flex-grow bg-slate-800/60 border border-white/10 rounded-lg p-2 text-sm text-white placeholder-gray-500 focus:ring-1 focus:ring-amber-500 focus:outline-none"
              />
              <select
                value={newRuleType}
                onChange={(e:any) => setNewRuleType(e.target.value)}
                className="bg-slate-800/60 border border-white/10 rounded-lg p-2 text-sm text-white focus:ring-1 focus:ring-amber-500 focus:outline-none"
              >
                  <option value="BLOCK">BLOCK</option>
                  <option value="ALLOW">ALLOW</option>
              </select>
              <NeonButton label="Add" onClick={addCustomRule} icon={Plus} color="amber" size="sm" fullWidth={false} />
          </div>
          <div className="mt-3 space-y-2">
              {customRules.map(rule => (
                  <div key={rule.domain} className="flex justify-between items-center text-sm bg-amber-950/30 p-2 rounded">
                      <p className={`font-mono ${rule.type === 'BLOCK' ? 'text-red-400' : 'text-green-400'}`}>
                          {rule.type}: {rule.domain}
                      </p>
                      <button onClick={() => removeCustomRule(rule.domain)} className="text-gray-500 hover:text-white">
                          <Trash2 size={14} />
                      </button>
                  </div>
              ))}
          </div>
      </GlassCard>
    </div>
  );

  const renderPurge = () => (
    <div className="space-y-6">
        <GlassCard borderColor="cyan">
            <h2 className="text-lg font-bold text-white flex items-center"><Wifi className="mr-2 text-cyan-400"/>Wireless ADB</h2>
            <p className="text-sm text-cyan-300/60 mb-4">Connect to your device to manage packages.</p>

            {!isConnected && (
                <>
                    <div className="flex space-x-2 mb-2">
                        <input type="text" placeholder="IP Address" value={ipAddress} onChange={e => setIpAddress(e.target.value)} className="w-1/2 bg-slate-800/60 border border-white/10 rounded-lg p-2 text-sm text-white placeholder-gray-500 focus:ring-1 focus:ring-cyan-500 focus:outline-none" />
                        <input type="text" placeholder="Port" value={port} onChange={e => setPort(e.target.value)} className="w-1/4 bg-slate-800/60 border border-white/10 rounded-lg p-2 text-sm text-white placeholder-gray-500 focus:ring-1 focus:ring-cyan-500 focus:outline-none" />
                        <input type="text" placeholder="Pairing Code" value={pairingCode} onChange={e => setPairingCode(e.target.value)} className="w-1/4 bg-slate-800/60 border border-white/10 rounded-lg p-2 text-sm text-white placeholder-gray-500 focus:ring-1 focus:ring-cyan-500 focus:outline-none" />
                    </div>
                    <NeonButton
                        label={isPairing ? "Pairing..." : "Pair & Connect"}
                        onClick={handlePair}
                        icon={Power}
                        color="cyan"
                        loading={isPairing}
                    />
                </>
            )}

            {isConnected && (
                <div className="bg-cyan-500/10 border border-cyan-500/20 text-cyan-300 p-3 rounded-lg text-sm flex items-center justify-between">
                    <span>Connected to {ipAddress}:{port}</span>
                    <button onClick={() => { setIsConnected(false); triggerHaptic('light'); }} className="font-bold">Disconnect</button>
                </div>
            )}
        </GlassCard>

        <GlassCard borderColor={isConnected ? "green" : "gray"}>
            <h2 className="text-lg font-bold text-white flex items-center"><LayoutGrid className="mr-2 text-gray-400"/>App Inspector</h2>
            <p className="text-sm text-gray-500 mb-4">View and manage installed packages.</p>

            {!isConnected && (
                <div className="text-center py-8">
                    <Power size={40} className="mx-auto text-cyan-400/20 mb-2"/>
                    <p className="text-cyan-300/40">Connect to ADB to populate this list.</p>
                </div>
            )}

            {isConnected && (
              <div className="space-y-2">
                {categories.map(category => (
                    <div key={category}>
                        <h3 className="text-sm font-bold text-gray-400 my-2 px-2">{category}</h3>
                        {apps.filter(app => app.category === category).map(app => (
                            <div key={app.pkg}>
                                <button onClick={() => setExpandedApp(expandedApp === app.pkg ? null : app.pkg)} className="w-full text-left bg-white/[0.03] hover:bg-white/[0.06] p-3 rounded-lg flex justify-between items-center transition-all">
                                    <div>
                                        <p className="font-medium text-white">{app.name}</p>
                                        <p className="text-xs text-gray-400 font-mono">{app.pkg}</p>
                                    </div>
                                    <div className="flex items-center space-x-3">
                                        <div className={`text-xs font-bold px-2 py-1 rounded-full
                                            ${app.risk === 'high' ? 'bg-red-500/20 text-red-300' :
                                              app.risk === 'medium' ? 'bg-amber-500/20 text-amber-300' : 'bg-green-500/20 text-green-300'}`}>
                                            {app.risk}
                                        </div>
                                        <ChevronRight size={18} className={`text-gray-500 transition-transform ${expandedApp === app.pkg ? 'rotate-90' : ''}`} />
                                    </div>
                                </button>
                                {expandedApp === app.pkg && (
                                    <div className="p-4 bg-slate-900/50 rounded-b-lg space-y-4">
                                        <div className="grid grid-cols-2 gap-4 text-sm">
                                            <div>
                                                <p className="text-gray-400 font-bold">Data Usage</p>
                                                <p className="text-white">{app.dataUsage}</p>
                                            </div>
                                             <div>
                                                <p className="text-gray-400 font-bold">Last Used</p>
                                                <p className="text-white">{app.lastUsed}</p>
                                            </div>
                                        </div>
                                        <div>
                                            <p className="text-gray-400 font-bold mb-1 text-sm">Permissions</p>
                                            <div className="flex flex-wrap gap-1">
                                                {app.permissions.map(p => <span key={p} className="text-xs bg-gray-700 text-gray-300 px-2 py-1 rounded-full font-mono">{p}</span>)}
                                            </div>
                                        </div>
                                        <div className="flex space-x-2 pt-2">
                                            <NeonButton onClick={() => handleAction('revoke', app.pkg)} label="Revoke Internet" icon={Shield} color="amber" size="sm" />
                                            <NeonButton onClick={() => handleAction('uninstall', app.pkg)} label="Uninstall" icon={Trash2} color="red" size="sm" />
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                ))}
              </div>
            )}
        </GlassCard>
    </div>
  );

  const renderUser = () => (
    <div className="space-y-6">
        <GlassCard borderColor="amber">
            <div className="flex items-center space-x-4">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-amber-500/30 to-orange-500/30 flex items-center justify-center">
                    <UserIcon size={40} className="text-amber-300" />
                </div>
                <div>
                    <h2 className="text-2xl font-bold text-white">Operative: {username}</h2>
                    <p className="text-sm text-amber-300/60">{getRankTitle(userLevel)}</p>
                    <p className="text-xs text-gray-500 mt-2">Core Version: {coreVersion}</p>
                </div>
            </div>
             <NeonButton onClick={handleLogout} label="Log Out" icon={LogOut} color="gray" size="sm" fullWidth={false} />
        </GlassCard>

        <GlassCard>
            <h3 className="font-bold text-white flex items-center mb-1"><Trophy size={18} className="mr-2 text-amber-400" />Rank & Progression</h3>
            <p className="text-xs text-gray-400 mb-4">Level {userLevel} - {getRankTitle(userLevel)}</p>
            <div className="w-full bg-amber-900/50 rounded-full h-2.5">
                <div className="bg-amber-500 h-2.5 rounded-full" style={{ width: `${progressPercent}%` }}></div>
            </div>
            <p className="text-xs text-right text-amber-300/70 mt-1">{currentLevelProgress} / 50 threats to next rank</p>
        </GlassCard>

        <div className="grid grid-cols-2 gap-4">
            <GlassCard>
                <h3 className="font-bold text-white flex items-center"><Target size={18} className="mr-2 text-red-400" />Total Blocked</h3>
                <p className="text-3xl font-bold text-white mt-2">{stats.blocked}</p>
            </GlassCard>
             <GlassCard>
                <h3 className="font-bold text-white flex items-center"><TrendingUp size={18} className="mr-2 text-green-400" />Data Saved</h3>
                <p className="text-3xl font-bold text-white mt-2">{stats.saved.toFixed(2)} <span className="text-xl">MB</span></p>
            </GlassCard>
        </div>
    </div>
  );

  if (!isLoggedIn) {
      return (
          <div className="w-full bg-slate-900 text-white min-h-screen">
              {renderLogin()}
          </div>
      );
  }

  return (
    <div className="bg-[#020617] text-white min-h-screen font-sans">
      <main className="p-6 pb-48">
        {activeTab === 'purge' && renderPurge()}
        {activeTab === 'shield' && renderShield()}
        {activeTab === 'insights' && renderInsights()}
        {activeTab === 'user' && renderUser()}
      </main>
      
      <TabBar active={activeTab} onChange={setActiveTab} />
    </div>
  );
}