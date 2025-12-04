import { useState, useMemo } from 'react';
// Adjust this import path based on where you put your types
import { AppData } from '../../types';

export const useAppManager = (apps: AppData[]) => {
  // 1. Search State
  const [search, setSearch] = useState('');

  // 2. Filter State
  const [filters, setFilters] = useState({
    category: 'All',   // e.g. 'Google', 'OEM'
    safety: 'All',     // e.g. 'Recommended', 'Unsafe'
    status: 'All',     // e.g. 'Enabled', 'Disabled'
    userId: 0          // e.g. 0 (Owner), 10 (Work)
  });

  // 3. The Logic Engine (Memoized for performance)
  const filteredApps = useMemo(() => {
    return apps.filter(app => {
      // --- Search Check ---
      const searchLower = search.toLowerCase();
      const matchesSearch =
        app.name.toLowerCase().includes(searchLower) ||
        app.pkg.toLowerCase().includes(searchLower);

      if (!matchesSearch) return false;

      // --- Category Check ---
      // Note: If filter is 'User', we check app.type, otherwise we check listCategory
      if (filters.category !== 'All') {
        if (filters.category === 'User') {
           if (app.type !== 'User') return false;
        } else {
           if (app.listCategory !== filters.category) return false;
        }
      }

      // --- Safety Check ---
      if (filters.safety !== 'All' && app.safety !== filters.safety) {
        return false;
      }

      // --- Status Check ---
      if (filters.status !== 'All' && app.status !== filters.status) {
        return false;
      }

      // --- User Check ---
      // (Optional: If your app data doesn't have userId attached to the app object yet,
      // you might need to handle this differently in the native bridge)
      // if (app.userId !== filters.userId) return false;

      return true;
    });
  }, [apps, search, filters]);

  // 4. Helper functions to make updating easier
  const updateFilter = (key: keyof typeof filters, value: string | number) => {
    setFilters(prev => ({ ...prev, [key]: value }));
  };

  return {
    search,
    setSearch,
    filters,
    updateFilter,
    filteredApps,
    count: filteredApps.length
  };
};