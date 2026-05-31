import React from 'react'

function App() {
  return (
    <div className="flex min-h-screen items-center justify-center p-6 bg-background">
      <div className="glass-card max-w-md w-full p-8 text-center space-y-6 animate-slide-in">
        <h1 className="text-3xl font-bold tracking-tighter text-foreground">
          EmergencyConnectUAE™
        </h1>
        <p className="text-muted-foreground text-sm">
          A distributed emergency coordination platform for public safety in the UAE.
        </p>
        <button className="w-full py-2.5 rounded-lg bg-primary text-primary-foreground font-semibold hover:bg-primary/90 transition-all active:scale-95">
          Sign In
        </button>
      </div>
    </div>
  )
}

export default App
