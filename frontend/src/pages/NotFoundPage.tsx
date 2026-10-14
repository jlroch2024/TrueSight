// Shown for any address the website does not have.
import { useNavigate } from 'react-router';
import { paths } from '../paths';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <section>
      <h1>Page Not Found</h1>
      <p className="muted">
        This page does not exist. <button type="button" className="inline-action" onClick={() => navigate(paths.home)}>Go to Home</button>
      </p>
    </section>
  );
}
