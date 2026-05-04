/**
 * Used when the SPA is served behind nginx and API calls are proxied on the same origin.
 * @see frontend/docker/nginx.conf
 */
export const environment = {
	production: true,
	apiUrl: '',
};
