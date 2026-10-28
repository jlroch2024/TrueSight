// Shown for any address the website does not have.
import { useNavigate } from 'react-router';
import { PageFrame } from '../components/PageFrame';
import { paths } from '../paths';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <PageFrame>
      <h1>Page Not Found</h1>
      <p>This address does not lead to a page in TrueSight.</p>
      <div className="actions">
        <button type="button" className="btn primary" onClick={() => navigate(paths.portfolios)}>Open My Portfolios</button>
      </div>
    </PageFrame>
  );
}
