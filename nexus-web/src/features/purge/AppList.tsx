import React from 'react';
import { AppData } from '../../types';

interface AppListProps {
  allApps: AppData[];
}

export const AppList = ({ allApps }: AppListProps) => {
  return (
    <div className="text-white p-4">
       {/* This component is a placeholder to satisfy the build.
           The actual list is currently rendered inside PurgeView.tsx */}
       <p className="text-gray-500 text-sm">
         Component Loaded: {allApps.length} apps available.
       </p>
    </div>
  );
};