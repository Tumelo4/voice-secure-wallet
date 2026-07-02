export interface AccessTokenProvider {
  getAccessToken(): string | Promise<string>;
}

export class StaticAccessTokenProvider implements AccessTokenProvider {
  private readonly token: string;

  constructor(token: string) {
    this.token = token;
  }

  getAccessToken(): string {
    return this.token;
  }
}
