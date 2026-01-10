import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { ChatComponent } from './components/chat/chat.component';
import { DashboardComponent } from './components/admin/dashboard/dashboard.component';
import { ScenariosComponent } from './components/admin/scenarios/scenarios.component';
import { AuditLogsComponent } from './components/admin/audit-logs/audit-logs.component';
import { SessionsComponent } from './components/admin/sessions/sessions.component';
import { SettingsComponent } from './components/admin/settings/settings.component';
import { PromptsComponent } from './components/admin/prompts/prompts.component';
// IntentsComponent removed - Intent detection now handled by Scenario entity
import { RbacComponent } from './components/admin/rbac/rbac.component';
import { RbacManagementComponent } from './components/admin/rbac/rbac-management.component';
import { ResponseMappingsComponent } from './components/admin/response-mappings/response-mappings.component';
import { FeedbackDashboardComponent } from './components/admin/feedback/feedback-dashboard.component';
import { FollowUpsComponent } from './components/admin/followups/followups.component';
import { PoliciesComponent } from './components/admin/policies/policies.component';
import { SystemConfigComponent } from './components/admin/system-config/system-config.component';
import { IntelligenceLayersComponent } from './components/admin/intelligence/intelligence-layers.component';
import { EntityPatternsComponent } from './components/admin/intelligence/entity-patterns.component';
import { EmbeddingsComponent } from './components/admin/intelligence/embeddings.component';
import { ContextMemoryComponent } from './components/admin/intelligence/context-memory.component';
import { ResponseTemplatesComponent } from './components/admin/response-templates/response-templates.component';
import { authGuard, adminGuard, operatorGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'chat', component: ChatComponent, canActivate: [authGuard] },
  { path: 'admin', component: DashboardComponent, canActivate: [adminGuard] },
  { path: 'admin/scenarios', component: ScenariosComponent, canActivate: [adminGuard] },
  { path: 'admin/prompts', component: PromptsComponent, canActivate: [adminGuard] },
  // { path: 'admin/intents', component: IntentsComponent, canActivate: [adminGuard] }, // REMOVED - Use Scenarios instead
  { path: 'admin/rbac', component: RbacManagementComponent, canActivate: [adminGuard] },
  { path: 'admin/response-mappings', component: ResponseMappingsComponent, canActivate: [adminGuard] },
  { path: 'admin/response-templates', component: ResponseTemplatesComponent, canActivate: [adminGuard] },
  { path: 'admin/feedback', component: FeedbackDashboardComponent, canActivate: [adminGuard] },
  { path: 'admin/followups', component: FollowUpsComponent, canActivate: [adminGuard] },
  { path: 'admin/policies', component: PoliciesComponent, canActivate: [adminGuard] },
  { path: 'admin/system-config', component: SystemConfigComponent, canActivate: [adminGuard] },
  // Intelligence System Configuration
  { path: 'admin/intelligence/layers', component: IntelligenceLayersComponent, canActivate: [adminGuard] },
  { path: 'admin/intelligence/entity-patterns', component: EntityPatternsComponent, canActivate: [adminGuard] },
  { path: 'admin/intelligence/embeddings', component: EmbeddingsComponent, canActivate: [adminGuard] },
  { path: 'admin/intelligence/context-memory', component: ContextMemoryComponent, canActivate: [adminGuard] },
  { path: 'admin/audit-logs', component: AuditLogsComponent, canActivate: [operatorGuard] },
  { path: 'admin/sessions', component: SessionsComponent, canActivate: [operatorGuard] },
  { path: 'admin/settings', component: SettingsComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '/login' }
];
