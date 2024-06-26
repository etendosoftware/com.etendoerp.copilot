import { References } from './references';

export const isDevelopment = () => {
  return process.env.NODE_ENV === "production" ? false : true;
};

export class RestUtils {
  static fetch = async (uri: string, options: RequestInit | undefined) => {
    let url;
    options = options || {};
    options.headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    };
    if (isDevelopment()) {
      options.headers = {
        Authorization: 'Basic ' + btoa('admin:admin'),
        ...(options.headers || {}),
      };
      url = References.DEV + uri;
    } else {
      url = References.PROD + uri;
    }
    return fetch(url, options);
  };
}
