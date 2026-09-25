// Shown for any address the website does not have.
import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section>
      <h1>Page Not Found</h1>
      <p className="muted">
        This page does not exist. <Link to="/">Go to Home</Link>
      </p>
    </section>
  );
}
