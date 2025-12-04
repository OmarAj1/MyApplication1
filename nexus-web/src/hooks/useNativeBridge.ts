import { useState, useEffect } from 'react';
import { AppData, ActionLog } from '../types';

export const useNativeBridge = () => {
  const [status, setStatus] = useState('Initializing...');
  const [apps, setApps] = useState<AppData[]>([]);
  const [users, setUsers] = useState<{id: number, name: string}[]>([{id: 0, name: 'Owner'}]);
  const [vpnActive, setVpnActive] = useState(false);
  const [history, setHistory] = useState<ActionLog[]>([]);

  // 1. NEW STATE: Store the data we get back from Android
  const [pairingData, setPairingData] = useState({ ip: '', port: '' });
  const [connectData, setConnectData] = useState({ ip: '', port: '' });

  const isNative = () => typeof (window as any).AndroidNative !== 'undefined';

  const actions = {
    pair: (ip: string, port: string, code: string) => {
        if (isNative()) (window as any).AndroidNative.pairAdb(ip, port, code);
    },
    connect: (ip: string, port: string) => {
        setStatus("Connecting...");
        if (isNative()) (window as any).AndroidNative.connectAdb(ip, port);
    },
    disconnect: () => {
         setStatus("Disconnected");
         // Add disconnect logic if needed
    },
    // 2. NEW ACTION: Call the Android function
    retrieve: () => {
        if (isNative()) (window as any).AndroidNative.retrieveConnectionInfo();
    },
    toggleVpn: () => {
        if (isNative() && (window as any).AndroidNative.startVpn) {
             if (vpnActive) (window as any).AndroidNative.stopVpn();
             else (window as any).AndroidNative.startVpn();
             setVpnActive(!vpnActive);
        }
    },
    exportHistory: () => {
        const text = history.map(h => `[${h.timestamp}] ${h.action} -> ${h.pkg}`).join('\n');
        if (isNative()) (window as any).AndroidNative.shareText("UAD Export", text);
    }
  };

  useEffect(() => {
    // Poll VPN Status
    const interval = setInterval(() => {
       if (isNative() && (window as any).AndroidNative.getVpnStatus) {
         setVpnActive((window as any).AndroidNative.getVpnStatus());
       }
    }, 2000);

    // Setup Global Listeners
    (window as any).adbStatus = (s: string) => setStatus(s);
    (window as any).receiveAppList = (b64: string) => {
        try {
            const json = JSON.parse(atob(b64));
            setApps(json);
            setStatus("Shell Active");
        } catch(e) { console.error(e); }
    };

    // 3. LISTENERS: When Android replies, update our state
    (window as any).onPairingServiceFound = (ip: string, port: any) => {
        setPairingData({ ip, port: port.toString() });
        setStatus('Pairing Info Found');
    };
    (window as any).onConnectServiceFound = (ip: string, port: any) => {
        setConnectData({ ip, port: port.toString() });
        setStatus('Ready to Connect');
    };

    return () => clearInterval(interval);
  }, []);

  // 4. EXPORT: Pass the data out to the app
  return { apps, users, status, vpnActive, history, actions, pairingData, connectData };
};