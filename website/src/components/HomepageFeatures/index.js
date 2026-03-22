import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Privacy by Design',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        Reverses PII into irreversible, cryptographically signed tokens using HMAC-SHA256,
        ensuring sensitive data never leaves your enterprise boundary.
      </>
    ),
  },
  {
    title: 'Zero-Dependency Core',
    Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
    description: (
      <>
        The <code>prism-core</code> engine contains no 3rd-party dependencies,
        minimizing your attack surface and ensuring high-performance execution.
      </>
    ),
  },
  {
    title: 'Spring AI Native',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        Seamlessly integrates as a non-invasive Advisor for Spring AI and as
        decorated chat wrappers for LangChain4j.
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
