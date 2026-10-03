import { useState } from 'react';
import { Modal } from '../ui';
import { RegisterForm, SignInForm } from './AuthForms';

/**
 * Asks a guest to sign in or create an account at the moment they reserve, without leaving
 * the page, so their chosen date and travellers are kept. `onDone(user)` resumes the action.
 */
export default function AuthGate({ open, onClose, onDone, intro }) {
  const [tab, setTab] = useState('register');

  return (
    <Modal open={open} onClose={onClose} title="Reserve your seats">
      {intro}
      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'register'} className={tab === 'register' ? 'on' : ''} onClick={() => setTab('register')}>
          New here
        </button>
        <button type="button" role="tab" aria-selected={tab === 'signin'} className={tab === 'signin' ? 'on' : ''} onClick={() => setTab('signin')}>
          I have an account
        </button>
      </div>
      {tab === 'register' ? (
        <RegisterForm onDone={onDone} submitLabel="Create account and reserve" />
      ) : (
        <SignInForm onDone={onDone} submitLabel="Sign in and reserve" />
      )}
    </Modal>
  );
}
