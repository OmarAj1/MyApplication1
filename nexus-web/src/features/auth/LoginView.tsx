import React, { useState } from 'react';
import { Key } from 'lucide-react';
import { GlassCard } from '../../components/ui/GlassCard';
import { NeonButton } from '../../components/ui/NeonButton';

export const LoginView = ({ onLogin }: { onLogin: (user: string) => void }) => {
    const [user, setUser] = useState('admin');
    const [pass, setPass] = useState('');

    return (
    <div className="w-full max-w-sm mx-auto flex flex-col justify-center h-screen p-8">
        <GlassCard borderColor="cyan">
            <h2 className="text-2xl font-bold text-center text-white mb-2">NEXUS CORE</h2>
            <input type="text" value={user} onChange={e => setUser(e.target.value)} className="bg-slate-800/60 border border-white/10 rounded-lg w-full p-3 mb-4 text-white" placeholder="Username"/>
            <input type="password" value={pass} onChange={e => setPass(e.target.value)} className="bg-slate-800/60 border border-white/10 rounded-lg w-full p-3 mb-6 text-white" placeholder="Password"/>
            <NeonButton label="Authenticate" color="cyan" icon={Key} onClick={() => onLogin(user)} />
        </GlassCard>
    </div>
    );
};