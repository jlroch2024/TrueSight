// cytoscape-cola publishes no type declarations of its own, and there is no @types/cytoscape-cola package. This says
// just enough for how SupplyChainGraph.tsx uses it: as a Cytoscape extension, registered once with cytoscape.use(cola).
declare module 'cytoscape-cola' {
  import type { Ext } from 'cytoscape';

  const cola: Ext;
  export default cola;
}
