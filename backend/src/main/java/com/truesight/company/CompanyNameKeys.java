package com.truesight.company;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * When the backend starts, brings every stored name's tidied form up to date with {@link CompanyDirectory#nameKey}.
 *
 * <p>Tidying improves over time (TS-87 added the SEC's "Corp" and "/DE" endings), but names saved earlier keep the
 * tidied form they were saved with, e.g. "nvidia corp". A report later naming "NVIDIA Corporation" is looked up as
 * "nvidia", misses it, and creates the same company again. Refreshing the stored forms prevents that. It does nothing
 * when they are already up to date, so every start after the first is quick.
 *
 * <ul>
 *   <li>A stored name whose new form belongs to no other name is updated.</li>
 *   <li>If its new form already belongs to another name of the same company, it adds nothing, so it is removed.</li>
 *   <li>If its new form belongs to a different company, the two were saved as separate companies before the fix.
 *       Merging them safely is not possible here, so both are left as they are, with a warning in the log.</li>
 * </ul>
 */
@Component
public class CompanyNameKeys implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyNameKeys.class);

    private final CompanyNameRepository names;

    public CompanyNameKeys(CompanyNameRepository names) {
        this.names = names;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        refresh();
    }

    /** Refreshes every stored name. Returns how many were updated or removed. */
    @Transactional
    public int refresh() {
        int changed = 0;
        for (CompanyName name : names.findAll()) {
            String key = CompanyDirectory.nameKey(name.getName());
            if (key.equals(name.getNameKey())) {
                continue;
            }
            CompanyName holder = names.findByNameKey(key).orElse(null);
            if (holder == null) {
                name.setNameKey(key);
                names.saveAndFlush(name);
                changed++;
            } else if (holder.getCompanyId().equals(name.getCompanyId())) {
                names.delete(name);
                names.flush();
                changed++;
            } else {
                log.warn("Companies {} and {} both have a name that now tidies to \"{}\". They were saved as two "
                        + "companies before names were tidied this way, and stay separate.",
                        holder.getCompanyId(), name.getCompanyId(), key);
            }
        }
        if (changed > 0) {
            log.info("Brought {} stored company names up to date with how names are now tidied.", changed);
        }
        return changed;
    }
}
