// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { apiRequest } from '../api/client';
import { NotificationsArea } from './NotificationsArea';

afterEach(cleanup);

describe('NotificationsArea', () => {
  it('shows absence of contact and saves only the email', async () => {
    const api = vi.fn()
      .mockResolvedValueOnce({ email: null })
      .mockResolvedValueOnce({ email: 'cliente@example.com' });
    render(<NotificationsArea api={api as typeof apiRequest} />);

    expect(await screen.findByText('No registrado')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Dirección de email'), { target: { value: 'cliente@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar email' }));

    await waitFor(() => expect(api).toHaveBeenCalledTimes(2));
    expect(api).toHaveBeenNthCalledWith(1, '/api/notifications/me/email');
    expect(api).toHaveBeenNthCalledWith(2, '/api/notifications/me/email', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'cliente@example.com' }),
    });
    expect(await screen.findByText('cliente@example.com')).toBeTruthy();
  });

  it('shows a forbidden error without claiming the email was saved', async () => {
    const api = vi.fn()
      .mockResolvedValueOnce({ email: null })
      .mockRejectedValueOnce(new Error('403: perfil sin permiso'));
    render(<NotificationsArea api={api as typeof apiRequest} />);

    await screen.findByText('No registrado');
    fireEvent.change(screen.getByLabelText('Dirección de email'), { target: { value: 'cliente@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar email' }));

    expect(await screen.findByText('403: perfil sin permiso')).toBeTruthy();
    expect(screen.getByText('No registrado')).toBeTruthy();
  });
});
