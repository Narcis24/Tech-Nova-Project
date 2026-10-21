import { AdminAccountResponse } from './adminAccountResponse';
import { AdminUserResponse } from './adminUserResponse';

export interface AdminUserDetailResponse {
    username: string;
    email?: string;
    roles: AdminUserResponse.RolesEnum[];
    createdAt: string;
    createdBy?: string;
    accounts: AdminAccountResponse[];
}