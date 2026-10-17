// Shown for any address the website does not have.
import { useNavigate } from 'react-router';
import { paths } from '../paths';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <section>
      <h1>Page Not Found</h1>
      <p className="muted">This address does not lead to a page in TrueSight.</p>
      <button type="button" onClick={() => navigate(paths.portfolios)}>Open My Portfolios</button>
    </section>
  );
}
