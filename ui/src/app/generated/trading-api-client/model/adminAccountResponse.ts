import { AccountResponse } from './accountResponse';

export interface AdminAccountResponse {
    accountId: string;
    holderName: string;
    cashBalance: number;
    status: AccountResponse.StatusEnum;
    riskBand: AdminAccountResponse.RiskBandEnum;
}

export namespace AdminAccountResponse {
    export const RiskBandEnum = {
        Low: 'LOW',
        Medium: 'MEDIUM',
        High: 'HIGH',
        Unknown: 'UNKNOWN',
    } as const;
    export type RiskBandEnum = typeof RiskBandEnum[keyof typeof RiskBandEnum];
}