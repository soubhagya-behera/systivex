import { TokenProvider, useToken } from './auth/TokenContext.jsx';
import { TokenGate } from './components/TokenGate.jsx';
import { TwinConsole } from './components/TwinConsole.jsx';

function Connector() {
  const { token, setToken, clear, hasToken } = useToken();

  if (!hasToken) {
    return (
      <main className="content" id="main-content">
        <TokenGate onSubmit={setToken} />
      </main>
    );
  }
  return <TwinConsole token={token} onDisconnect={clear} />;
}

export default function App() {
  return (
    <TokenProvider>
      <Connector />
    </TokenProvider>
  );
}
