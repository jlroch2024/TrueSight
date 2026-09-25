// The sidebar's links, in order. Each story that adds a page adds one line here, and its <Route> in App.tsx.
// Labels are in Title Case.
export type NavItem = { path: string; label: string };

export const NAV_ITEMS: NavItem[] = [{ path: '/', label: 'Home' }];
