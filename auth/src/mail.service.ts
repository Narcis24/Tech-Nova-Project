import { Injectable } from '@nestjs/common';
import nodemailer, { type Transporter } from 'nodemailer';

/**
 * Sends the service's emails over SMTP. In development SMTP_HOST points at Mailpit, which catches
 * every email and shows it at http://localhost:8025 instead of delivering it.
 */
@Injectable()
export class MailService {
  private readonly transporter: Transporter;
  private readonly from: string;

  constructor(
    host = process.env.SMTP_HOST ?? 'localhost',
    port = Number(process.env.SMTP_PORT ?? 1025),
    from = process.env.MAIL_FROM ?? 'TechNova <no-reply@technova.local>',
  ) {
    this.transporter = nodemailer.createTransport({ host, port });
    this.from = from;
  }

  async sendPasswordReset(to: string, resetLink: string): Promise<void> {
    await this.transporter.sendMail({
      from: this.from,
      to,
      subject: 'Reset your TechNova password',
      text: [
        'Someone asked to reset the password for your TechNova account.',
        '',
        `Reset it here: ${resetLink}`,
        '',
        "The link expires in 30 minutes and works once. If you didn't ask for this, you can ignore this email.",
      ].join('\n'),
      html: `
        <p>Someone asked to reset the password for your TechNova account.</p>
        <p><a href="${resetLink}">Reset your password</a></p>
        <p>The link expires in 30 minutes and works once. If you didn't ask for this, you can ignore this email.</p>
      `,
    });
  }
}
