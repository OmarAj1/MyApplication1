import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

// Define the Theme structure here so we can use it in App.tsx too
export interface ThemeConfig {
  accentColor: string;
  darkColor: string;
  particles: string[];
}

const ParticleSystem = React.memo(({ particles }: { particles: string[] }) => {
  const items = useMemo(() => {
    return Array.from({ length: 15 }).map((_, i) => ({
      id: i,
      char: particles[i % particles.length],
      left: `${Math.random() * 100}%`,
      top: `${Math.random() * 100}%`,
      scale: 0.5 + Math.random() * 1,
      duration: 15 + Math.random() * 20,
      delay: Math.random() * 5
    }));
  }, [particles]);

  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      <AnimatePresence mode='wait'>
        {items.map(p => (
          <motion.div
            key={`${p.id}-${p.char}`}
            className="absolute text-2xl select-none opacity-20" // Low opacity so it's subtle
            style={{ left: p.left, top: p.top }}
            initial={{ opacity: 0, scale: 0 }}
            animate={{
              y: [0, -100, 0],
              x: [0, 20, -20, 0],
              opacity: [0, 0.3, 0],
              rotate: [0, 10, -10, 0],
              scale: p.scale
            }}
            transition={{
              duration: p.duration,
              repeat: Infinity,
              ease: "linear"
            }}
          >
            {p.char}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
});

export const AtmosphericBackground = React.memo(({ theme }: { theme: ThemeConfig }) => {
  return (
    <div className="fixed inset-0 overflow-hidden pointer-events-none z-0">
      {/* Base Background */}
      <div className="absolute inset-0 bg-[#020617]" />

      {/* Primary Blob (Top Left) */}
      <motion.div
        className="absolute top-[-20%] left-[-10%] w-[90vw] h-[90vw] rounded-full blur-[120px] opacity-30"
        animate={{ backgroundColor: theme.accentColor }}
        transition={{ duration: 2 }}
      />

      {/* Secondary Blob (Bottom Right) */}
      <motion.div
        className="absolute bottom-[-20%] right-[-10%] w-[80vw] h-[80vw] rounded-full blur-[100px] opacity-20"
        animate={{ backgroundColor: theme.darkColor }}
        transition={{ duration: 2 }}
      />

      {/* Floating Emojis */}
      <ParticleSystem particles={theme.particles} />

      {/* Noise Texture for "Premium" Feel */}
      <div className="absolute inset-0 opacity-[0.05] mix-blend-overlay"
           style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")` }}
      />
    </div>
  );
});