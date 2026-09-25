// The one way the website talks to the backend. Every page calls api(), never fetch() directly, so each request
// gets the same three things without repeating them:
//
//   1. the /api prefix, so a page writes api('/portfolios') rather than the full address;
//   2. the sign-in token, when there is one, as "Authorization: Bearer <token>";
//   3. errors turned into an ApiError whose message is the backend's own "message", ready to show the user.
//
//   const portfolios = await api<Portfolio[]>('/portfolios');
//   await api('/portfolios', { method: 'POST', body: JSON.stringify({ name: 'Tech' }) });

const TOKEN_KEY = 'truesight.token';

/** An error from the backend, or from failing to reach it. `message` is written for the user to read. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/** Where the sign-in token is kept in the browser. The Sign Up and Log In story uses set() and clear(). */
export const tokenStore = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string): void => localStorage.setItem(TOKEN_KEY, token),
  clear: (): void => localStorage.removeItem(TOKEN_KEY),
};

export async function api<T = void>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = tokenStore.get();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  // A file upload (FormData) sets its own content type; everything else sent is JSON.
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  let response: Response;
  try {
    response = await fetch(`/api${path}`, { ...options, headers });
  } catch {
    throw new ApiError('Cannot reach the server. Is the backend running?', 0);
  }

  if (!response.ok) {
    throw new ApiError(await readMessage(response), response.status);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** The backend always sends {"message": "..."} on failure. Anything else gets a general message. */
async function readMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string') {
      return body.message;
    }
  } catch {
    // Not JSON: fall through to the general message.
  }
  return `Something went wrong (error ${response.status}).`;
}
