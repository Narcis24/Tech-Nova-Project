import nodemailer from 'nodemailer';
import { MailService } from './mail.service.js';

const LINK = 'http://localhost:4200/reset-password#token=abc123';

describe('MailService', () => {
  const sendMail = vi.fn();

  beforeEach(() => {
    sendMail.mockReset().mockResolvedValue({});
    vi.spyOn(nodemailer, 'createTransport').mockReturnValue({
      sendMail,
    } as never);
  });

  afterEach(() => vi.restoreAllMocks());

  it('connects to the configured SMTP server', () => {
    new MailService('mailpit', 1025);

    expect(nodemailer.createTransport).toHaveBeenCalledWith({
      host: 'mailpit',
      port: 1025,
    });
  });

  it('sends the reset link to the user from the configured sender', async () => {
    await new MailService(
      'mailpit',
      1025,
      'TechNova <no-reply@technova.local>',
    ).sendPasswordReset('john.doe@example.com', LINK);

    const mail = sendMail.mock.calls[0][0];
    expect(mail.from).toBe('TechNova <no-reply@technova.local>');
    expect(mail.to).toBe('john.doe@example.com');
    expect(mail.subject).toBe('Reset your TechNova password');
    expect(mail.text).toContain(LINK);
    expect(mail.text).toContain('expires in 30 minutes');
    expect(mail.html).toContain(`href="${LINK}"`);
  });

  it('passes a sending failure on to the caller', async () => {
    sendMail.mockRejectedValue(new Error('connection refused'));

    await expect(
      new MailService().sendPasswordReset('john.doe@example.com', LINK),
    ).rejects.toThrow('connection refused');
  });
});
