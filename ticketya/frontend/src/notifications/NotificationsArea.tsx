import { useEffect, useState, type FormEvent } from 'react';
import type { apiRequest } from '../api/client';

type EmailContact = { email: string | null };

export function NotificationsArea({ api }: { api: typeof apiRequest }) {
  const [email, setEmail] = useState('');
  const [savedEmail, setSavedEmail] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let current = true;
    api<EmailContact>('/api/notifications/me/email')
      .then((contact) => {
        if (!current) return;
        setSavedEmail(contact.email);
        setEmail(contact.email ?? '');
      })
      .catch((error: unknown) => {
        if (current) setMessage(error instanceof Error ? error.message : 'No se pudo consultar el email.');
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => { current = false; };
  }, [api]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage('');
    try {
      const contact = await api<EmailContact>('/api/notifications/me/email', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim() }),
      });
      setSavedEmail(contact.email);
      setEmail(contact.email ?? '');
      setMessage('Email guardado. Los próximos avisos podrán enviarse a esta dirección.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo guardar el email.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <section aria-labelledby="contact-heading" className="panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Notificaciones</p>
          <h2 id="contact-heading">Email de contacto</h2>
        </div>
      </div>
      <p>Usamos esta dirección para los avisos de compra y cambios de fecha de tus eventos.</p>
      {loading ? <p role="status">Consultando contacto…</p> : (
        <>
          <p>Contacto actual: <strong>{savedEmail ?? 'No registrado'}</strong></p>
          <form onSubmit={save}>
            <label htmlFor="contact-email">Dirección de email</label>
            <input id="contact-email" name="email" type="email" autoComplete="email" required maxLength={254}
              value={email} onChange={(event) => setEmail(event.target.value)} />
            <button type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar email'}</button>
          </form>
        </>
      )}
      {message && <p role="status">{message}</p>}
    </section>
  );
}
