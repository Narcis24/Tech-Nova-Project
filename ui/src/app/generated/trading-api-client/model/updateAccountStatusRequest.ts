import { AccountResponse } from './accountResponse';

export interface UpdateAccountStatusRequest {
    status: AccountResponse.StatusEnum;
}