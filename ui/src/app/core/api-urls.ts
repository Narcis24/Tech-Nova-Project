/** Relative so requests go through the dev proxy (proxy.conf.json) and work behind any host in prod. */

/** Auth service: global prefix `/auth` + controller `v1/auth` */
export const AUTH_BASE = '/auth/v1/auth';

/** Trading API: servlet context path `/api`; controllers add `/v1/...` */
export const API_BASE = '/api';
