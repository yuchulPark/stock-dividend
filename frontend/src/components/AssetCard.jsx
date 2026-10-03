import { Link } from 'react-router-dom'
import { assetTypeLabel, marketLabel } from '../utils/formatCurrency.js'
export default function AssetCard({ asset }) {
  return <Link className="asset-card" to={`/asset/${encodeURIComponent(asset.market)}/${encodeURIComponent(asset.ticker)}`}>
    <span className="asset-initial" aria-hidden="true">{asset.ticker?.slice(0, 2)}</span>
    <div className="asset-card-body"><span className="eyebrow">{asset.ticker}</span><h3>{asset.name}</h3><span className="asset-tags">{marketLabel(asset.market)} · {assetTypeLabel(asset.assetType)} · {asset.currency}</span></div>
    <span className="card-arrow" aria-hidden="true">↗</span>
  </Link>
}
