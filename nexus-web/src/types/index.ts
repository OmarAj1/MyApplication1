export interface AppData {
  name: string;
  pkg: string;
  type: 'System' | 'User';
  status: 'Enabled' | 'Disabled' | 'Uninstalled' | 'Unknown';
  iconBase64?: string;
  listCategory?: string;
  safety?: 'Recommended' | 'Advanced' | 'Expert' | 'Unsafe' | 'Unknown';
}

export interface ActionLog {
  timestamp: string;
  action: string;
  pkg: string;
  user: number;
}