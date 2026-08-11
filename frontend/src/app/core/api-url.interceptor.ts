import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../environments/environment';

const API_PATH = '/api/v1';

/**
 * Keeps services environment-agnostic: they continue to call /api/v1 while
 * production sends those requests to API Gateway. Other URLs are untouched.
 */
export const apiUrlInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API_PATH)) return next(request);

  const relativePath = request.url.slice(API_PATH.length);
  return next(request.clone({ url: `${environment.apiUrl}${relativePath}` }));
};
