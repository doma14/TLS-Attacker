/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol;

import de.rub.nds.modifiablevariable.ModifiableVariable;
import de.rub.nds.modifiablevariable.util.RandomHelper;
import de.rub.nds.modifiablevariable.util.ReflectionHelper;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 */
public abstract class ModifiableVariableHolder implements Serializable {

    protected static final Logger LOGGER = LogManager.getLogger(ModifiableVariableHolder.class.getName());

    private TlsContext context = new TlsContext();

    /**
     * Lists all the modifiable variables declared in the class
     * 
     * @return
     */
    public List<Field> getAllModifiableVariableFields() {
        return ReflectionHelper.getFieldsUpTo(this.getClass(), null, ModifiableVariable.class);
    }

    /**
     * Returns a random field representing a modifiable variable from this class
     * 
     * @return
     */
    public Field getRandomModifiableVariableField() {
        List<Field> fields = getAllModifiableVariableFields();
        int randomField = context.getRandom().nextInt(fields.size());
        return fields.get(randomField);
    }

    /**
     * Returns a list of all the modifiable variable holders in the object,
     * including this instance
     * 
     * @return
     */
    public List<ModifiableVariableHolder> getAllModifiableVariableHolders() {
        List<ModifiableVariableHolder> holders = new LinkedList<>();
        holders.add(this);
        return holders;
    }

    /**
     * Returns a random modifiable variable holder
     * 
     * @return
     */
    public ModifiableVariableHolder getRandomModifiableVariableHolder() {
        List<ModifiableVariableHolder> holders = getAllModifiableVariableHolders();
        int randomHolder = context.getRandom().nextInt(holders.size());
        return holders.get(randomHolder);
    }
}
