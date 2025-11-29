import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { ChatComponent } from './components/chat/chat.component';
import { DashboardComponent } from './components/admin/dashboard/dashboard.component';
import { ScenariosComponent } from './components/admin/scenarios/scenarios.component';
import { AuditLogsComponent } from './components/admin/audit-logs/audit-logs.component';
import { SessionsComponent } from './components/admin/sessions/sessions.component';
import { SettingsComponent } from './components/admin/settings/settings.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'chat', component: ChatComponent },
  { path: 'admin', component: DashboardComponent },
  { path: 'admin/scenarios', component: ScenariosComponent },
  { path: 'admin/audit-logs', component: AuditLogsComponent },
  { path: 'admin/sessions', component: SessionsComponent },
  { path: 'admin/settings', component: SettingsComponent },
  { path: '**', redirectTo: '/login' }
];
