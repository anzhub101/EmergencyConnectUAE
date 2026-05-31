import { NavLink, useNavigate } from 'react-router-dom';
import { LayoutDashboard, ShieldCheck, LogOut, Siren } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Sidebar = () => {
  const { role, signOut } = useAuth();
  const navigate = useNavigate();

  const canAdmin = role === 'DISPATCHER' || role === 'SYSTEM_ADMIN' || role === 'HOSPITAL_ADMIN';

  const handleSignOut = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
      isActive ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-muted'
    }`;

  return (
    <aside className="w-60 shrink-0 h-screen border-r border-border bg-card flex flex-col p-4">
      <div className="flex items-center gap-2 px-2 py-3 mb-4">
        <Siren className="text-primary" size={22} />
        <span className="font-bold tracking-tight text-foreground">EmergencyConnect</span>
      </div>

      <nav className="flex flex-col gap-1">
        <NavLink to="/" end className={linkClass}>
          <LayoutDashboard size={18} /> Dashboard
        </NavLink>
        {canAdmin && (
          <NavLink to="/admin" className={linkClass}>
            <ShieldCheck size={18} /> Administration
          </NavLink>
        )}
      </nav>

      <div className="mt-auto">
        {role && (
          <div className="px-3 py-2 text-xs text-muted-foreground">
            Signed in as <span className="font-semibold text-foreground">{role.replace('_', ' ')}</span>
          </div>
        )}
        <button
          onClick={handleSignOut}
          className="flex w-full items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-muted-foreground hover:text-destructive hover:bg-muted transition-colors"
        >
          <LogOut size={18} /> Sign Out
        </button>
      </div>
    </aside>
  );
};
