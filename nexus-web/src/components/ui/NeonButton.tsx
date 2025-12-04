import React from 'react';
import { LucideIcon } from 'lucide-react';
import { motion } from 'framer-motion';

interface NeonButtonProps {
  onClick: (e: any) => void;
  active?: boolean;
  icon?: LucideIcon;
  label: string;
  color?: "green" | "cyan" | "red" | "gray";
  loading?: boolean;
  small?: boolean;
}

export const NeonButton = ({ onClick, active, icon: Icon, label, color = "green", loading = false, small=false }: NeonButtonProps) => {
  const colors: Record<string, string> = {
    green: "from-green-500/90 to-emerald-600/90 shadow-green-500/20",
    cyan: "from-cyan-500/90 to-blue-600/90 shadow-cyan-500/20",
    red: "from-red-500/90 to-rose-600/90 shadow-red-500/20",
    gray: "from-gray-700/90 to-gray-800/90 shadow-gray-500/10"
  };

  return (
    <motion.button
      whileHover={{ scale: 1.02 }}
      whileTap={{ scale: 0.96 }}
      onClick={onClick}
      disabled={loading}
      className={`relative overflow-hidden group ${small ? 'p-2' : 'w-full py-4 px-4'} text-sm rounded-xl font-medium tracking-wide transition-all duration-300
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
    </motion.button>
  );
};