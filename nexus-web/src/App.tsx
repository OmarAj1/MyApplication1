import React, { useState, useMemo } from 'react';
import { LogOut, User as UserIcon } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

// Imports
import TabBar from './components/ui/TabBar';
import { GlassCard } from './components/ui/GlassCard';
import { NeonButton } from './components/ui/NeonButton';
import { AtmosphericBackground, ThemeConfig } from './components/ui/AtmosphericBackground';

import { PurgeView } from './features/purge/PurgeView';
import { ConnectionView } from './features/connection/ConnectionView';
import { ShieldView } from './features/shield/ShieldView';
import { HistoryView } from './features/history/HistoryView';
import { LoginView } from './features/auth/LoginView';

import { useNativeBridge } from './hooks/useNativeBridge';

export default function App() {
  const [activeTab, setActiveTab] = useState('purge');
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [username, setUsername] = useState('admin');

  const {
        apps, users, status, vpnActive, history, actions,
        pairingData, connectData // <--- Get the new data
    } = useNativeBridge();

  const theme = useMemo<ThemeConfig>(() => {
    if (!isLoggedIn) return {
        accentColor: '#06b6d4',
        darkColor: '#0f172a',
        particles: ['🔐', '🔑', '🛡️', '🔒', '🗝️']
    };

    // 2. SHIELD TAB
    if (activeTab === 'shield') {
      return vpnActive
        ? { accentColor: '#22c55e', darkColor: '#14532d', particles: ['🛡️', '🔒', '✅', '🛰️', '✨'] } // Secure
        : { accentColor: '#ef4444', darkColor: '#7f1d1d', particles: ['🚨', '🚫', '⚠️', '🔓', '💢'] }; // Insecure
    }

    // 3. PURGE TAB
    if (activeTab === 'purge') {
        return { accentColor: '#3b82f6', darkColor: '#1e40af', particles: ['📦', '📲', '🧹', '⚡', '💾'] };
    }

    // 4. HISTORY TAB
    if (activeTab === 'history') {
        return { accentColor: '#a855f7', darkColor: '#581c87', particles: ['🕰️', '📜', '🔍', '📅', '📝'] };
    }

    // 5. USER TAB
    return { accentColor: '#f59e0b', darkColor: '#78350f', particles: ['⚙️', '👤', '🔧', '💎', '🔑'] };

  }, [activeTab, vpnActive, isLoggedIn]);


  // --- RENDER HELPERS ---
  const renderPurgeContent = () => {
      const isConnected = status === 'Shell Active' || status === 'Connected';

      if (!isConnected) {
        return (
          <ConnectionView
            status={status}
            onPair={actions.pair}
            onConnect={actions.connect}
            // PASS THE NEW PROPS DOWN:
            onRetrieve={actions.retrieve}
            pairingData={pairingData}
            connectData={connectData}
          />
        );
      }

      return <PurgeView allApps={apps} users={users} onDisconnect={actions.disconnect} />;
    };

  return (
    <div className="relative w-full min-h-screen font-sans select-none overflow-hidden bg-[#020617] text-white">

      {/* 1. The Dynamic Background Layer */}
      <AtmosphericBackground theme={theme} />

      <main className="relative z-10 p-6 pb-48 min-h-screen">

        {/* 2. Login Check */}
        {!isLoggedIn ? (
           <LoginView onLogin={(user) => { setUsername(user); setIsLoggedIn(true); }} />
        ) : (
          // 3. Page Transition Wrapper
          <AnimatePresence mode="wait">
             <motion.div
                key={activeTab} // Changing this key triggers the exit/enter animation
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -10 }}
                transition={{ duration: 0.2 }}
             >
                {activeTab === 'purge' && renderPurgeContent()}

                {activeTab === 'shield' && (
                  <ShieldView isActive={vpnActive} onToggle={actions.toggleVpn} />
                )}

                {activeTab === 'history' && (
                  <HistoryView history={history} onExport={actions.exportHistory} />
                )}

                {activeTab === 'user' && (
                    <GlassCard borderColor="amber">
                        <div className="flex items-center space-x-4">
                            <div className="w-20 h-20 rounded-full bg-amber-500/20 flex items-center justify-center">
                                <UserIcon size={40} className="text-amber-300" />
                            </div>
                            <div>
                                <h2 className="text-2xl font-bold">Operative: {username}</h2>
                                <p className="text-amber-300/60 text-sm">Privacy Warlord</p>
                            </div>
                        </div>
                        <div className="mt-4">
                            <NeonButton onClick={() => setIsLoggedIn(false)} label="Log Out" icon={LogOut} color="gray" />
                        </div>
                    </GlassCard>
                )}
             </motion.div>
          </AnimatePresence>
        )}
      </main>

      {/* 4. Tab Bar (Only show if logged in) */}
      {isLoggedIn && <TabBar active={activeTab} onChange={setActiveTab} />}

    </div>
  );
}