import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';
// This is the entry point of the React application.
// It finds the 'root' div in index.html and renders the <App /> component into it.

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

const root = ReactDOM.createRoot(rootElement);

root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);