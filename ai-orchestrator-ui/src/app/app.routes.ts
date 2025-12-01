import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { ChatComponent } from './components/chat/chat.component';
import { DashboardComponent } from './components/admin/dashboard/dashboard.component';
import { ScenariosComponent } from './components/admin/scenarios/scenarios.component';
import { AuditLogsComponent } from './components/admin/audit-logs/audit-logs.component';
import { SessionsComponent } from './components/admin/sessions/sessions.component';
import { SettingsComponent } from './components/admin/settings/settings.component';
import { PromptsComponent } from './components/admin/prompts/prompts.component';
import { IntentsComponent } from './components/admin/intents/intents.component';
import { RbacComponent } from './components/admin/rbac/rbac.component';
import { authGuard, adminGuard, operatorGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'chat', component: ChatComponent, canActivate: [authGuard] },
  { path: 'admin', component: DashboardComponent, canActivate: [adminGuard] },
  { path: 'admin/scenarios', component: ScenariosComponent, canActivate: [adminGuard] },
  { path: 'admin/prompts', component: PromptsComponent, canActivate: [adminGuard] },
  { path: 'admin/intents', component: IntentsComponent, canActivate: [adminGuard] },
  { path: 'admin/rbac', component: RbacComponent, canActivate: [adminGuard] },
  { path: 'admin/audit-logs', component: AuditLogsComponent, canActivate: [operatorGuard] },
  { path: 'admin/sessions', component: SessionsComponent, canActivate: [operatorGuard] },
  { path: 'admin/settings', component: SettingsComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '/login' }
];
