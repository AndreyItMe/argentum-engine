/**
 * Account presence widget for the landing screen — deliberately separate from the navigation
 * buttons so the sign-in state reads as a distinct, persistent affordance. Anchored top-right.
 *
 *  - signed in  → "Signed in as <name>" (opens the profile) + a Log out action
 *  - anonymous  → a single Log in button that opens the magic-link modal
 *
 * Renders nothing when the server has accounts disabled, so a no-accounts deployment shows no
 * sign-in UI at all (the whole point — a login form there can only fail).
 */
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { LoginModal } from '@/components/auth/LoginModal'
import { useAuthStore } from '@/store/authStore'
import styles from './AuthWidget.module.css'

export function AuthWidget() {
  const navigate = useNavigate()
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const status = useAuthStore((s) => s.status)
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const [loginOpen, setLoginOpen] = useState(false)

  if (!accountsEnabled) return null

  return (
    <div className={styles.widget}>
      {status === 'authenticated' && user ? (
        <>
          <button
            type="button"
            className={styles.identity}
            onClick={() => navigate('/profile')}
            title="View your profile"
          >
            <span className={styles.dot} aria-hidden="true" />
            <span className={styles.labels}>
              <span className={styles.muted}>Signed in as</span>
              <span className={styles.name}>{user.displayName}</span>
            </span>
          </button>
          {user.isAdmin && (
            <button
              type="button"
              className={styles.admin}
              onClick={() => navigate('/admin')}
              title="Open the admin dashboard"
            >
              Admin
            </button>
          )}
          <button type="button" className={styles.logout} onClick={logout} title="Sign out">
            Log out
          </button>
        </>
      ) : (
        <button type="button" className={styles.login} onClick={() => setLoginOpen(true)}>
          Log in
        </button>
      )}
      <LoginModal open={loginOpen} onClose={() => setLoginOpen(false)} />
    </div>
  )
}
